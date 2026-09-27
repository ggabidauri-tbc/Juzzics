package com.example.juzzics.features.nearby.data

import android.content.ContentUris
import android.content.Context
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import androidx.core.content.edit
import com.example.juzzics.features.musics.domain.model.MusicFileDomain
import com.example.juzzics.features.musics.domain.repo.MusicRepo
import com.example.juzzics.features.nearby.domain.ConnectedFriend
import com.example.juzzics.features.nearby.domain.NearbyDevice
import com.example.juzzics.features.nearby.domain.NearbyState
import com.example.juzzics.features.nearby.domain.PendingConnection
import com.example.juzzics.features.nearby.domain.RemoteCommand
import com.example.juzzics.features.nearby.domain.RemoteNowPlaying
import com.example.juzzics.features.nearby.domain.RemoteSong
import com.example.juzzics.features.nearby.domain.SongTransfer
import com.example.juzzics.features.player.OpenPlayerRequests
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
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream

/**
 * Connects phones running Juzzics without internet (Google Nearby Connections:
 * Bluetooth to find each other, then Wi-Fi Direct for the data), so friends can browse
 * each other's songs, play / control music on each other's phones, and send a song
 * to the other phone to hear it there (the whole file is sent, then played).
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

    // ---------------------- songs sent between phones ----------------------

    /** file payloads coming in, by payload id */
    private val incomingFiles = mutableMapOf<Long, Payload>()
    /** what each incoming file is (comes as a separate small message, in any order) */
    private val incomingInfo = mutableMapOf<Long, NearbyMessage>()
    /** incoming files fully received */
    private val receivedPayloads = mutableSetOf<Long>()
    /** incoming files we asked for ("listen here"): the player opens when they start */
    private val requestedPayloads = mutableSetOf<Long>()
    /** files this phone is sending */
    private val outgoingPayloads = mutableSetOf<Long>()

    /** gets one of their songs to play it on this phone */
    fun listenHere(endpointId: String, song: RemoteSong) {
        val key = requestKey(endpointId, song.id)
        if (_state.value.transfers.any { it.key == key }) return
        addTransfer(SongTransfer(key, endpointId, song.title, friendName(endpointId), incoming = true))
        send(endpointId, NearbyMessage(NearbyMessage.STREAM_REQUEST, songId = song.id))
    }

    /** sends one of this phone's songs to play it on theirs */
    fun sendToFriend(endpointId: String, songId: Long) = sendSongFile(endpointId, songId, askedByFriend = false)

    /** this phone's songs, to pick one to send */
    suspend fun mySongs(): List<RemoteSong> =
        musicRepo.getAllLocalMusicFiles().getOrDefault(emptyList()).map { it.toRemote() }

    private fun sendSongFile(endpointId: String, songId: Long, askedByFriend: Boolean) {
        scope.launch {
            val song = musicRepo.getAllLocalMusicFiles().getOrDefault(emptyList()).find { it.id == songId }
            val file = song?.let {
                val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, songId)
                withContext(Dispatchers.IO) { runCatching { context.contentResolver.openFileDescriptor(uri, "r") }.getOrNull() }
            }
            if (song == null || file == null) {
                if (askedByFriend) send(endpointId, NearbyMessage(NearbyMessage.FILE_FAILED, songId = songId))
                else _state.update { it.copy(error = "Couldn't open that song to send it") }
                return@launch
            }
            val payload = Payload.fromFile(file)
            outgoingPayloads += payload.id
            addTransfer(
                SongTransfer(outKey(payload.id), endpointId, song.title.orEmpty(), friendName(endpointId), incoming = false, progress = 0f)
            )
            // first what it is, then the file itself
            send(
                endpointId,
                NearbyMessage(
                    NearbyMessage.FILE_INFO,
                    songId = songId,
                    payloadId = payload.id,
                    title = song.title,
                    artist = song.artist?.takeUnless { it == "<unknown>" },
                    durationMs = song.duration,
                    extension = song.data?.substringAfterLast('.', "")?.take(5),
                )
            )
            client.sendPayload(endpointId, payload)
                .addOnFailureListener { e -> transferFailed(outKey(payload.id), payload.id, "Couldn't send \"${song.title}\". ${e.explain()}") }
        }
    }

    private fun receiveFileInfo(endpointId: String, message: NearbyMessage) {
        val payloadId = message.payloadId ?: return
        incomingInfo[payloadId] = message
        val asked = removeTransfer(requestKey(endpointId, message.songId ?: 0))
        if (asked) requestedPayloads += payloadId
        addTransfer(
            SongTransfer(inKey(payloadId), endpointId, message.title.orEmpty(), friendName(endpointId), incoming = true, progress = 0f)
        )
        finishIncoming(endpointId, payloadId)
    }

    private fun onFileUpdate(endpointId: String, update: PayloadTransferUpdate) {
        val id = update.payloadId
        val outgoing = id in outgoingPayloads
        val incoming = id in incomingFiles || id in incomingInfo
        if (!outgoing && !incoming) return // one of the small messages
        val key = if (outgoing) outKey(id) else inKey(id)
        when (update.status) {
            PayloadTransferUpdate.Status.IN_PROGRESS ->
                if (update.totalBytes > 0) setProgress(key, update.bytesTransferred.toFloat() / update.totalBytes)

            PayloadTransferUpdate.Status.SUCCESS -> if (outgoing) {
                outgoingPayloads -= id
                removeTransfer(key)
            } else {
                receivedPayloads += id
                finishIncoming(endpointId, id)
            }

            PayloadTransferUpdate.Status.FAILURE, PayloadTransferUpdate.Status.CANCELED ->
                transferFailed(key, id, "The song stopped half-way. Stay closer together and try again.")
        }
    }

    private fun transferFailed(key: String, payloadId: Long, error: String) {
        outgoingPayloads -= payloadId
        incomingFiles -= payloadId
        incomingInfo -= payloadId
        receivedPayloads -= payloadId
        requestedPayloads -= payloadId
        if (removeTransfer(key)) _state.update { it.copy(error = error) }
    }

    /** file and its info are both here: keep a copy and play it */
    private fun finishIncoming(endpointId: String, payloadId: Long) {
        if (payloadId !in receivedPayloads) return
        val info = incomingInfo.remove(payloadId) ?: return
        val payload = incomingFiles.remove(payloadId) ?: return
        receivedPayloads -= payloadId
        val asked = requestedPayloads.remove(payloadId)
        scope.launch {
            val file = withContext(Dispatchers.IO) { runCatching { saveReceived(payload, info) }.getOrNull() }
            removeTransfer(inKey(payloadId))
            if (file == null) {
                _state.update { it.copy(error = "Couldn't save \"${info.title}\" on this phone") }
                return@launch
            }
            val song = MusicFileDomain(
                // negative: never mixed up with the phone's own songs (the player plays it from `data`)
                id = -System.currentTimeMillis(),
                title = info.title,
                artist = info.artist,
                data = file.absolutePath,
                duration = info.durationMs ?: 0,
                icon = Uri.EMPTY,
            )
            player.playQueue(listOf(song), 0, source = "From ${friendName(endpointId)}")
            if (asked) OpenPlayerRequests.request()
        }
    }

    /** copies the received song into the app's cache (keeps only the last few) */
    private fun saveReceived(payload: Payload, info: NearbyMessage): File {
        val received = payload.asFile() ?: error("not a file")
        val dir = File(context.cacheDir, RECEIVED_DIR).apply { mkdirs() }
        val extension = info.extension?.filter { it.isLetterOrDigit() }?.take(5)?.ifBlank { null } ?: "audio"
        val target = File(dir, "song_${System.currentTimeMillis()}.$extension")
        received.asParcelFileDescriptor().use { descriptor ->
            FileInputStream(descriptor.fileDescriptor).use { input -> target.outputStream().use { input.copyTo(it) } }
        }
        received.deleteOriginal()
        dir.listFiles()
            ?.sortedByDescending { it.lastModified() }
            ?.drop(KEEP_RECEIVED_SONGS)
            ?.forEach { it.delete() }
        return target
    }

    /** Nearby keeps its own copy of received files (in Downloads): not needed after we copied it */
    @Suppress("DEPRECATION")
    private fun Payload.File.deleteOriginal() {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) asUri()?.let { context.contentResolver.delete(it, null, null) }
            else asJavaFile()?.delete()
        }
    }

    private fun addTransfer(transfer: SongTransfer) =
        _state.update { it.copy(transfers = it.transfers.filterNot { t -> t.key == transfer.key } + transfer) }

    /** true if it was there */
    private fun removeTransfer(key: String): Boolean {
        val had = _state.value.transfers.any { it.key == key }
        if (had) _state.update { it.copy(transfers = it.transfers.filterNot { t -> t.key == key }) }
        return had
    }

    private fun setProgress(key: String, progress: Float) {
        val current = _state.value.transfers.find { it.key == key } ?: return
        // every 2%: progress updates come very often
        if (current.progress != null && progress - current.progress < 0.02f && progress < 1f) return
        _state.update { state ->
            state.copy(transfers = state.transfers.map { if (it.key == key) it.copy(progress = progress) else it })
        }
    }

    private fun requestKey(endpointId: String, songId: Long) = "request:$endpointId:$songId"
    private fun inKey(payloadId: Long) = "in:$payloadId"
    private fun outKey(payloadId: Long) = "out:$payloadId"

    private fun friendName(endpointId: String) =
        names[endpointId] ?: _state.value.friends.find { it.endpointId == endpointId }?.name ?: "a friend"

    private fun MusicFileDomain.toRemote() = RemoteSong(
        id = id,
        title = title.orEmpty(),
        artist = artist?.takeUnless { it == "<unknown>" }.orEmpty(),
        durationMs = duration,
    )

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
            if (payload.type == Payload.Type.FILE) {
                // a song starts arriving (its info comes as a separate message)
                incomingFiles[payload.id] = payload
                return
            }
            if (payload.type != Payload.Type.BYTES) return
            val bytes = payload.asBytes() ?: return
            val message = runCatching { gson.fromJson(String(bytes), NearbyMessage::class.java) }.getOrNull() ?: return
            handle(endpointId, message)
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) =
            onFileUpdate(endpointId, update)
    }

    // ---------------------- messages ----------------------

    private fun handle(endpointId: String, message: NearbyMessage) {
        when (message.type) {
            NearbyMessage.LIBRARY_REQUEST -> sendLibrary(endpointId)
            NearbyMessage.LIBRARY_PAGE -> receiveLibraryPage(endpointId, message)
            NearbyMessage.PLAY -> message.songId?.let { playForFriend(endpointId, it) }
            NearbyMessage.COMMAND -> message.command?.let { runCatching { RemoteCommand.valueOf(it) }.getOrNull() }
                ?.let(::runCommand)
            NearbyMessage.STREAM_REQUEST -> message.songId?.let { sendSongFile(endpointId, it, askedByFriend = true) }
            NearbyMessage.FILE_INFO -> receiveFileInfo(endpointId, message)
            NearbyMessage.FILE_FAILED -> message.songId?.let { songId ->
                if (removeTransfer(requestKey(endpointId, songId))) {
                    _state.update { it.copy(error = "${friendName(endpointId)}'s phone couldn't send that song") }
                }
            }
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
            val songs = mySongs()
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
        _state.update { state ->
            state.copy(
                friends = state.friends.filterNot { it.endpointId == endpointId },
                transfers = state.transfers.filterNot { it.endpointId == endpointId },
            )
        }
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

        /** folder in the app's cache for songs friends sent */
        const val RECEIVED_DIR = "nearby_songs"
        const val KEEP_RECEIVED_SONGS = 10
    }
}
