package com.example.juzzics.features.nearby.data

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.edit
import com.example.juzzics.features.musics.domain.repo.MusicRepo
import com.example.juzzics.features.nearby.domain.ConnectedFriend
import com.example.juzzics.features.nearby.domain.NearbyDevice
import com.example.juzzics.features.nearby.domain.NearbyState
import com.example.juzzics.features.nearby.domain.PendingConnection
import com.example.juzzics.features.nearby.domain.RemoteCommand
import com.example.juzzics.features.nearby.domain.RemoteNowPlaying
import com.example.juzzics.features.nearby.domain.RemoteSong
import com.example.juzzics.features.player.PlayerController
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Connects phones running Juzzics without internet (Google Nearby Connections:
 * Bluetooth to find each other, then Wi-Fi Direct for the data), so friends can browse
 * each other's songs and play / control music on each other's phones.
 *
 * App-wide (one instance), so connections stay up while you switch screens.
 */
class NearbyManager(
    private val context: Context,
    private val player: PlayerController,
    private val musicRepo: MusicRepo,
) {
    private val client: ConnectionsClient = Nearby.getConnectionsClient(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val gson = Gson()
    private val prefs = context.getSharedPreferences("nearby", Context.MODE_PRIVATE)
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val _state = MutableStateFlow(NearbyState(deviceName = savedOrDefaultName()))
    val state: StateFlow<NearbyState> = _state.asStateFlow()

    /** names of phones we're (becoming) connected to */
    private val names = mutableMapOf<String, String>()
    /** connection requests this phone turned down, so we don't report them as "declined" */
    private val rejectedByMe = mutableSetOf<String>()
    private var advertisingInFlight = false
    private var discoveryInFlight = false
    private var stopSearchJob: Job? = null
    private var nowPlayingJob: Job? = null

    // ---------------------- this phone ----------------------

    fun setDeviceName(name: String) {
        val clean = name.trim().take(40)
        prefs.edit { putString(KEY_NAME, clean) }
        _state.update { it.copy(deviceName = clean) }
        // advertise under the new name
        if (_state.value.sharing) {
            client.stopAdvertising()
            startAdvertising()
        }
    }

    /** on: friends nearby can find this phone, browse its songs and play them here */
    fun setSharing(on: Boolean) {
        if (on == _state.value.sharing) return
        if (on) startAdvertising() else {
            client.stopAdvertising()
            _state.update { it.copy(sharing = false) }
        }
    }

    private fun startAdvertising() {
        if (advertisingInFlight) return // a double tap on the switch
        advertisingInFlight = true
        val options = AdvertisingOptions.Builder().setStrategy(STRATEGY).build()
        client.startAdvertising(displayName(), SERVICE_ID, connectionCallback, options)
            .addOnSuccessListener { _state.update { it.copy(sharing = true, error = null) } }
            .addOnFailureListener { e ->
                if (e.statusCode() == ConnectionsStatusCodes.STATUS_ALREADY_ADVERTISING) {
                    // Play services was still advertising for us (e.g. the app was restarted): that's fine
                    _state.update { it.copy(sharing = true, error = null) }
                } else {
                    _state.update { it.copy(sharing = false, error = "Couldn't start sharing. ${e.explain()}") }
                }
            }
            .addOnCompleteListener { advertisingInFlight = false }
    }

    // ---------------------- finding friends ----------------------

    /** looks for friends' phones for a minute (searching uses battery) */
    fun startSearching() {
        if (discoveryInFlight) return
        discoveryInFlight = true
        _state.update { it.copy(found = emptyList(), error = null) }
        val options = DiscoveryOptions.Builder().setStrategy(STRATEGY).build()
        client.startDiscovery(SERVICE_ID, discoveryCallback, options)
            .addOnSuccessListener { searchingStarted() }
            .addOnFailureListener { e ->
                if (e.statusCode() == ConnectionsStatusCodes.STATUS_ALREADY_DISCOVERING) searchingStarted()
                else _state.update { it.copy(searching = false, error = "Couldn't search. ${e.explain()}") }
            }
            .addOnCompleteListener { discoveryInFlight = false }
    }

    private fun searchingStarted() {
        _state.update { it.copy(searching = true) }
        stopSearchJob?.cancel()
        stopSearchJob = scope.launch {
            delay(SEARCH_DURATION_MS)
            stopSearching()
        }
    }

    fun stopSearching() {
        stopSearchJob?.cancel()
        client.stopDiscovery()
        _state.update { it.copy(searching = false) }
    }

    fun connect(device: NearbyDevice) {
        if (_state.value.pending != null) return // already pairing with someone
        client.requestConnection(displayName(), device.endpointId, connectionCallback)
            .addOnFailureListener { e ->
                when (e.statusCode()) {
                    // both phones tapped Connect at the same time: the other request carries on
                    ConnectionsStatusCodes.STATUS_ALREADY_CONNECTED_TO_ENDPOINT,
                    ConnectionsStatusCodes.STATUS_OUT_OF_ORDER_API_CALL -> Unit
                    else -> _state.update { it.copy(error = "Couldn't connect to ${device.name}. ${e.explain()}") }
                }
            }
    }

    /** both phones showed the same code: go ahead (the connection opens once the other phone agrees too) */
    fun acceptPending() {
        val pending = _state.value.pending ?: return
        if (pending.accepted) return
        _state.update { it.copy(pending = pending.copy(accepted = true)) }
        client.acceptConnection(pending.endpointId, payloadCallback)
    }

    fun rejectPending() {
        val pending = _state.value.pending ?: return
        rejectedByMe += pending.endpointId
        client.rejectConnection(pending.endpointId)
        _state.update { it.copy(pending = null) }
    }

    fun disconnect(endpointId: String) {
        client.disconnectFromEndpoint(endpointId)
        removeFriend(endpointId)
    }

    fun clearError() = _state.update { it.copy(error = null) }

    // ---------------------- controlling a friend's phone ----------------------

    fun requestLibrary(endpointId: String) = send(endpointId, NearbyMessage(NearbyMessage.LIBRARY_REQUEST))

    /** plays one of their songs on their phone */
    fun playOnFriend(endpointId: String, songId: Long) =
        send(endpointId, NearbyMessage(NearbyMessage.PLAY, songId = songId))

    fun sendCommand(endpointId: String, command: RemoteCommand) =
        send(endpointId, NearbyMessage(NearbyMessage.COMMAND, command = command.name))

    // ---------------------- callbacks ----------------------

    private val discoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            _state.update { state ->
                if (state.found.any { it.endpointId == endpointId }) state
                else state.copy(found = state.found + NearbyDevice(endpointId, info.endpointName))
            }
        }

        override fun onEndpointLost(endpointId: String) {
            _state.update { state -> state.copy(found = state.found.filterNot { it.endpointId == endpointId }) }
        }
    }

    private val connectionCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            names[endpointId] = info.endpointName
            _state.update {
                it.copy(
                    pending = PendingConnection(
                        endpointId = endpointId,
                        name = info.endpointName,
                        code = info.authenticationDigits,
                        incoming = info.isIncomingConnection,
                    )
                )
            }
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            _state.update { it.copy(pending = null) }
            val iRejected = rejectedByMe.remove(endpointId)
            if (result.status.statusCode == ConnectionsStatusCodes.STATUS_OK) {
                val name = names[endpointId] ?: "Friend"
                _state.update { state ->
                    state.copy(
                        friends = state.friends.filterNot { it.endpointId == endpointId } +
                                ConnectedFriend(endpointId, name),
                        found = state.found.filterNot { it.endpointId == endpointId },
                    )
                }
                stopSearching()
                requestLibrary(endpointId)
                sendNowPlaying(endpointId)
                startNowPlayingUpdates()
            } else if (!iRejected) {
                val who = names.remove(endpointId) ?: "the other phone"
                val error = if (result.status.statusCode == ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED)
                    "$who didn't accept the connection" else "Couldn't connect to $who. Try again closer together."
                _state.update { it.copy(error = error) }
            }
        }

        override fun onDisconnected(endpointId: String) = removeFriend(endpointId)
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type != Payload.Type.BYTES) return
            val bytes = payload.asBytes() ?: return
            val message = runCatching { gson.fromJson(String(bytes), NearbyMessage::class.java) }.getOrNull() ?: return
            handle(endpointId, message)
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) = Unit
    }

    // ---------------------- messages ----------------------

    private fun handle(endpointId: String, message: NearbyMessage) {
        when (message.type) {
            NearbyMessage.LIBRARY_REQUEST -> sendLibrary(endpointId)
            NearbyMessage.LIBRARY_PAGE -> receiveLibraryPage(endpointId, message)
            NearbyMessage.PLAY -> message.songId?.let { playForFriend(endpointId, it) }
            NearbyMessage.COMMAND -> message.command?.let { runCatching { RemoteCommand.valueOf(it) }.getOrNull() }
                ?.let(::runCommand)
            NearbyMessage.NOW_PLAYING -> updateFriend(endpointId) { friend ->
                friend.copy(
                    nowPlaying = message.title?.let {
                        RemoteNowPlaying(it, message.artist.orEmpty(), message.isPlaying == true)
                    }
                )
            }
        }
    }

    /** our song list, in pages small enough for one message */
    private fun sendLibrary(endpointId: String) {
        scope.launch {
            val songs = musicRepo.getAllLocalMusicFiles().getOrDefault(emptyList()).map {
                RemoteSong(
                    id = it.id,
                    title = it.title.orEmpty(),
                    artist = it.artist?.takeUnless { a -> a == "<unknown>" }.orEmpty(),
                    durationMs = it.duration,
                )
            }
            val pages = songs.chunked(SONGS_PER_PAGE).ifEmpty { listOf(emptyList()) }
            pages.forEachIndexed { index, page ->
                send(endpointId, NearbyMessage(NearbyMessage.LIBRARY_PAGE, songs = page, page = index, pageCount = pages.size))
            }
        }
    }

    private fun receiveLibraryPage(endpointId: String, message: NearbyMessage) {
        val page = message.page ?: 0
        updateFriend(endpointId) { friend ->
            val library = if (page == 0) message.songs.orEmpty() else friend.library + message.songs.orEmpty()
            friend.copy(library = library, libraryComplete = page >= (message.pageCount ?: 1) - 1)
        }
    }

    /** a friend picked one of our songs: play our library from it */
    private fun playForFriend(endpointId: String, songId: Long) {
        scope.launch {
            val songs = musicRepo.getAllLocalMusicFiles().getOrDefault(emptyList())
            val index = songs.indexOfFirst { it.id == songId }
            if (index >= 0) player.playQueue(songs, index, source = "Picked by ${names[endpointId] ?: "a friend"}")
        }
    }

    private fun runCommand(command: RemoteCommand) {
        when (command) {
            RemoteCommand.TOGGLE -> player.togglePlayPause()
            RemoteCommand.NEXT -> player.next()
            RemoteCommand.PREVIOUS -> player.previous()
            RemoteCommand.VOLUME_UP -> adjustVolume(AudioManager.ADJUST_RAISE)
            RemoteCommand.VOLUME_DOWN -> adjustVolume(AudioManager.ADJUST_LOWER)
        }
    }

    private fun adjustVolume(direction: Int) =
        audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)

    /** tells connected friends what this phone is playing, whenever it changes */
    private fun startNowPlayingUpdates() {
        if (nowPlayingJob?.isActive == true) return
        nowPlayingJob = scope.launch {
            player.state
                .map { it.currentSong?.id to it.isPlaying }
                .distinctUntilChanged()
                .collect { _state.value.friends.forEach { friend -> sendNowPlaying(friend.endpointId) } }
        }
    }

    private fun sendNowPlaying(endpointId: String) {
        val playing = player.state.value
        val song = playing.currentSong
        send(
            endpointId,
            NearbyMessage(
                NearbyMessage.NOW_PLAYING,
                title = song?.title,
                artist = song?.artist?.takeUnless { it == "<unknown>" },
                isPlaying = playing.isPlaying,
            )
        )
    }

    private fun send(endpointId: String, message: NearbyMessage) {
        val bytes = gson.toJson(message).toByteArray()
        client.sendPayload(endpointId, Payload.fromBytes(bytes))
    }

    // ---------------------- helpers ----------------------

    private fun updateFriend(endpointId: String, change: (ConnectedFriend) -> ConnectedFriend) {
        _state.update { state ->
            state.copy(friends = state.friends.map { if (it.endpointId == endpointId) change(it) else it })
        }
    }

    private fun removeFriend(endpointId: String) {
        names.remove(endpointId)
        _state.update { state -> state.copy(friends = state.friends.filterNot { it.endpointId == endpointId }) }
        if (_state.value.friends.isEmpty()) {
            nowPlayingJob?.cancel()
            nowPlayingJob = null
        }
    }

    private fun Exception.statusCode(): Int? = (this as? ApiException)?.statusCode

    /** a readable reason instead of "8034: MISSING_PERMISSION_ACCESS_COARSE_LOCATION" */
    private fun Exception.explain(): String {
        val code = statusCode() ?: return message.orEmpty()
        return when (code) {
            // 8034 / 8035: coarse / fine location missing
            8034, 8035 ->
                "Juzzics needs the Location permission for this (Android uses it to find Bluetooth devices)."
            // 8030..8040: other permissions missing (Bluetooth, Wi-Fi, nearby devices)
            in 8030..8040 ->
                "Juzzics needs the Nearby devices permission. Allow it in the app's settings."
            ConnectionsStatusCodes.STATUS_BLUETOOTH_ERROR -> "Turn Bluetooth on and try again."
            ConnectionsStatusCodes.STATUS_RADIO_ERROR -> "Turn Bluetooth and Wi-Fi on and try again."
            ConnectionsStatusCodes.STATUS_ENDPOINT_UNKNOWN -> "That phone isn't visible any more. Look again."
            else -> "(${ConnectionsStatusCodes.getStatusCodeString(code)})"
        }
    }

    private fun displayName() = _state.value.deviceName.ifBlank { defaultName() }

    private fun savedOrDefaultName(): String =
        prefs.getString(KEY_NAME, null)?.takeIf { it.isNotBlank() } ?: defaultName()

    private fun defaultName(): String =
        Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)
            ?.takeIf { it.isNotBlank() } ?: Build.MODEL

    private companion object {
        /** only Juzzics phones find each other */
        const val SERVICE_ID = "juzzics.nearby"

        /** several phones can connect to each other */
        val STRATEGY: Strategy = Strategy.P2P_CLUSTER

        const val SEARCH_DURATION_MS = 60_000L

        /** keeps each message well under Nearby's ~32 KB limit, even with long titles */
        const val SONGS_PER_PAGE = 80

        const val KEY_NAME = "device_name"
    }
}
