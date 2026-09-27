package com.example.juzzics.features.nearby.data

import android.content.ContentUris
import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import android.util.Base64
import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import androidx.core.content.edit
import com.example.juzzics.common.artwork.loadSongArtwork
import com.example.juzzics.features.lyrics.domain.model.LyricsDomain
import com.example.juzzics.features.lyrics.domain.model.lrcToPlainText
import com.example.juzzics.features.lyrics.domain.repo.LyricsRepo
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
import com.example.juzzics.features.player.stream.GrowingFiles
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

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
    private val lyricsRepo: LyricsRepo,
    private val received: ReceivedSongs,
) {
    private val client: ConnectionsClient = Nearby.getConnectionsClient(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val gson = Gson()
    private val prefs = context.getSharedPreferences("nearby", Context.MODE_PRIVATE)
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val _state = MutableStateFlow(
        NearbyState(deviceName = savedOrDefaultName(), letFriendsSave = prefs.getBoolean(KEY_LET_SAVE, true))
    )
    val state: StateFlow<NearbyState> = _state.asStateFlow()

    private val party = NearbyParty(
        player = player,
        scope = scope,
        send = { endpointId, message -> send(endpointId, message) },
        sendSong = { endpointId, song, partyKey -> scope.launch { sendSongFile(endpointId, song, partyKey) } },
        nameOf = ::friendName,
        onState = { party -> _state.update { it.copy(party = party) } },
    )

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

    /** friends may keep the songs this phone sends them */
    fun setLetFriendsSave(allow: Boolean) {
        prefs.edit { putBoolean(KEY_LET_SAVE, allow) }
        _state.update { it.copy(letFriendsSave = allow) }
    }

    /** party mode: connected friends' phones play along with this one */
    fun startParty() = party.startHosting(_state.value.friends.map { it.endpointId })

    /** host: ends it for everyone; guest: leaves it */
    fun endParty() = party.end()

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

    /**
     * A song streaming in. Its bytes go to [path] as they arrive; once the first bit is there
     * it's announced (and played / handed to the party) while the rest keeps arriving.
     */
    private class IncomingSong(val payloadId: Long, val endpointId: String, val songId: Long) {
        var info: NearbyMessage? = null
        var payload: Payload? = null
        var path: String? = null
        @Volatile var written = 0L
        @Volatile var failed = false
        var announced = false
        var reading = false
    }

    /** songs streaming in, by payload id */
    private val incoming = mutableMapOf<Long, IncomingSong>()
    /** incoming songs we asked for ("listen here"): the player opens when they start */
    private val requestedPayloads = mutableSetOf<Long>()
    /** songs this phone is sending, with their sizes (for progress) */
    private val outgoingSizes = mutableMapOf<Long, Long>()
    /** pictures and lyrics of incoming songs (separate small messages) */
    private val incomingArt = mutableMapOf<Long, String>()
    private val incomingLyrics = mutableMapOf<Long, LyricsDomain>()

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

    /** a friend asked for one of this phone's songs, or picked one to send */
    private fun sendSongFile(endpointId: String, songId: Long, askedByFriend: Boolean) {
        scope.launch {
            val song = musicRepo.getAllLocalMusicFiles().getOrDefault(emptyList()).find { it.id == songId }
            if (song == null || !sendSongFile(endpointId, song)) {
                if (askedByFriend) send(endpointId, NearbyMessage(NearbyMessage.FILE_FAILED, songId = songId))
                else _state.update { it.copy(error = "Couldn't open that song to send it") }
            }
        }
    }

    /**
     * streams [song]: first what it is (plus its picture and lyrics), then the file's bytes,
     * which the other phone can start playing before they've all arrived.
     * [partyKey] set: it's for party mode (the guest follows the host with it).
     * false if the file couldn't be opened
     */
    private suspend fun sendSongFile(endpointId: String, song: MusicFileDomain, partyKey: Long? = null): Boolean {
        val file = withContext(Dispatchers.IO) {
            runCatching {
                val path = song.data
                if (song.id < 0 && path != null) {
                    // a song a friend sent us (party host passing it on)
                    ParcelFileDescriptor.open(File(path), ParcelFileDescriptor.MODE_READ_ONLY)
                } else {
                    val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, song.id)
                    context.contentResolver.openFileDescriptor(uri, "r")
                }
            }.getOrNull()
        } ?: return false
        val (art, lyrics) = withContext(Dispatchers.IO) {
            artworkForSending(song.id) to runCatching { lyricsRepo.observeSavedLyrics(song.id).first() }.getOrNull()
        }

        val size = file.statSize
        val payload = Payload.fromStream(file)
        outgoingSizes[payload.id] = size
        addTransfer(
            SongTransfer(outKey(payload.id), endpointId, song.title.orEmpty(), friendName(endpointId), incoming = false, progress = 0f)
        )
        val path = song.data.orEmpty()
        send(
            endpointId,
            NearbyMessage(
                NearbyMessage.FILE_INFO,
                songId = song.id,
                payloadId = payload.id,
                title = song.title,
                artist = song.artist?.takeUnless { it == "<unknown>" },
                durationMs = song.duration,
                extension = path.substringAfterLast('.', "").take(5),
                sizeBytes = size,
                canSave = _state.value.letFriendsSave,
                purpose = partyKey?.let { NearbyMessage.PURPOSE_PARTY },
                partyKey = partyKey,
            )
        )
        art?.let { send(endpointId, NearbyMessage(NearbyMessage.SONG_ART, payloadId = payload.id, art = it)) }
        lyrics?.let {
            // synced lyrics carry the plain text too: send one, it has to fit in one message
            val message = if (it.synced != null) NearbyMessage(NearbyMessage.SONG_LYRICS, payloadId = payload.id, synced = it.synced)
            else NearbyMessage(NearbyMessage.SONG_LYRICS, payloadId = payload.id, lyrics = it.lyrics)
            send(endpointId, message)
        }
        client.sendPayload(endpointId, payload)
            .addOnFailureListener { e -> sendFailed(payload.id, "Couldn't send \"${song.title}\". ${e.explain()}") }
        return true
    }

    /** the song's picture as a small JPEG in base64, small enough for one message; null if none */
    private fun artworkForSending(songId: Long): String? {
        val bitmap = loadSongArtwork(context, songId) ?: return null
        val scale = ART_SIZE.toFloat() / maxOf(bitmap.width, bitmap.height)
        val small = if (scale < 1f) {
            Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
        } else bitmap
        for (quality in listOf(80, 65, 50, 35)) {
            val bytes = ByteArrayOutputStream().also { small.compress(Bitmap.CompressFormat.JPEG, quality, it) }.toByteArray()
            val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            if (base64.length < MAX_ART_CHARS) return base64
        }
        return null
    }

    private fun incomingSong(payloadId: Long, endpointId: String) =
        incoming.getOrPut(payloadId) { IncomingSong(payloadId, endpointId, received.newId()) }

    private fun receiveFileInfo(endpointId: String, message: NearbyMessage) {
        val payloadId = message.payloadId ?: return
        val song = incomingSong(payloadId, endpointId)
        song.info = message
        song.path?.let { GrowingFiles.setTotalBytes(it, message.sizeBytes ?: -1L) }
        val asked = removeTransfer(requestKey(endpointId, message.songId ?: 0L))
        if (asked) requestedPayloads += payloadId
        addTransfer(
            SongTransfer(inKey(payloadId), endpointId, message.title.orEmpty(), friendName(endpointId), incoming = true, progress = 0f)
        )
        announceIfReady(song)
    }

    /** the song's bytes start arriving: write them to a file as they come */
    private fun receiveStream(endpointId: String, payload: Payload) {
        val song = incomingSong(payload.id, endpointId)
        song.payload = payload
        if (song.reading) return
        song.reading = true
        val file = received.songFile(song.songId, "audio")
        song.path = file.absolutePath
        GrowingFiles.start(file.absolutePath, song.info?.sizeBytes ?: -1L)
        scope.launch {
            val success = withContext(Dispatchers.IO) {
                runCatching {
                    val input = payload.asStream()?.asInputStream() ?: error("no stream")
                    input.use {
                        file.outputStream().use { out ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                val read = input.read(buffer)
                                if (read < 0) break
                                out.write(buffer, 0, read)
                                song.written += read
                                GrowingFiles.progress(file.absolutePath, song.written)
                                if (!song.announced && song.written >= READY_BYTES) {
                                    withContext(Dispatchers.Main) { announceIfReady(song) }
                                }
                                val size = song.info?.sizeBytes ?: 0L
                                if (size > 0) setProgress(inKey(song.payloadId), song.written.toFloat() / size)
                            }
                        }
                    }
                    val size = song.info?.sizeBytes ?: 0L
                    !song.failed && (size <= 0 || song.written >= size)
                }.getOrDefault(false)
            }
            GrowingFiles.finish(file.absolutePath, success)
            incoming -= song.payloadId
            removeTransfer(inKey(song.payloadId))
            if (success) {
                announceIfReady(song, complete = true)
            } else {
                receiveFailed(song)
            }
        }
    }

    /**
     * enough of the song is here to start playing (or all of it, [complete]): keep it
     * (with picture and lyrics) and play it / hand it to the party
     */
    private fun announceIfReady(song: IncomingSong, complete: Boolean = false) {
        val info = song.info ?: return
        val path = song.path ?: return
        if (song.announced || song.failed) return
        if (!complete && song.written < READY_BYTES) return
        song.announced = true
        val asked = requestedPayloads.remove(song.payloadId)
        val art = incomingArt.remove(song.payloadId)
        val lyrics = incomingLyrics.remove(song.payloadId)
        scope.launch {
            val receivedSong = ReceivedSong(
                id = song.songId,
                title = info.title.orEmpty(),
                artist = info.artist.orEmpty(),
                durationMs = info.durationMs ?: 0L,
                path = path,
                from = friendName(song.endpointId),
                canSave = info.canSave == true,
                extension = info.extension?.filter { it.isLetterOrDigit() }?.take(5)?.ifBlank { null },
            )
            val artBytes = art?.let { runCatching { Base64.decode(it, Base64.NO_WRAP) }.getOrNull() }
            received.add(receivedSong, artBytes, lyrics)

            val partyKey = info.partyKey
            if (info.purpose == NearbyMessage.PURPOSE_PARTY && partyKey != null) {
                party.onSongReady(partyKey, received.toMusicFile(receivedSong))
            } else {
                player.playQueue(listOf(received.toMusicFile(receivedSong)), 0, source = "From ${receivedSong.from}")
                if (asked) OpenPlayerRequests.request()
            }
        }
    }

    private fun receiveFailed(song: IncomingSong) {
        song.failed = true
        requestedPayloads -= song.payloadId
        incomingArt -= song.payloadId
        incomingLyrics -= song.payloadId
        removeTransfer(inKey(song.payloadId))
        scope.launch { received.remove(song.songId) }
        song.path?.let { File(it).delete() }
        _state.update { it.copy(error = "\"${song.info?.title ?: "The song"}\" stopped half-way. Stay closer together and try again.") }
    }

    private fun sendFailed(payloadId: Long, error: String) {
        outgoingSizes -= payloadId
        if (removeTransfer(outKey(payloadId))) _state.update { it.copy(error = error) }
    }

    private fun onFileUpdate(endpointId: String, update: PayloadTransferUpdate) {
        val id = update.payloadId
        val outgoingSize = outgoingSizes[id]
        val incomingSong = incoming[id]
        when {
            outgoingSize != null -> when (update.status) {
                PayloadTransferUpdate.Status.IN_PROGRESS -> {
                    val total = if (update.totalBytes > 0) update.totalBytes else outgoingSize
                    if (total > 0) setProgress(outKey(id), update.bytesTransferred.toFloat() / total)
                }
                PayloadTransferUpdate.Status.SUCCESS -> {
                    outgoingSizes -= id
                    removeTransfer(outKey(id))
                }
                PayloadTransferUpdate.Status.FAILURE, PayloadTransferUpdate.Status.CANCELED ->
                    sendFailed(id, "Sending the song stopped half-way. Stay closer together and try again.")
            }

            incomingSong != null -> if (
                update.status == PayloadTransferUpdate.Status.FAILURE ||
                update.status == PayloadTransferUpdate.Status.CANCELED
            ) {
                // stops the reading loop, which then cleans up
                incomingSong.failed = true
                runCatching { incomingSong.payload?.asStream()?.asInputStream()?.close() }
            }
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
                party.onFriendConnected(endpointId)
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
            if (payload.type == Payload.Type.STREAM) {
                // a song starts arriving (its info comes as a separate message)
                receiveStream(endpointId, payload)
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
        if (party.handle(endpointId, message)) return
        when (message.type) {
            NearbyMessage.LIBRARY_REQUEST -> sendLibrary(endpointId)
            NearbyMessage.LIBRARY_PAGE -> receiveLibraryPage(endpointId, message)
            NearbyMessage.PLAY -> message.songId?.let { playForFriend(endpointId, it) }
            NearbyMessage.COMMAND -> message.command?.let { runCatching { RemoteCommand.valueOf(it) }.getOrNull() }
                ?.let(::runCommand)
            NearbyMessage.STREAM_REQUEST -> message.songId?.let { sendSongFile(endpointId, it, askedByFriend = true) }
            NearbyMessage.FILE_INFO -> receiveFileInfo(endpointId, message)
            NearbyMessage.SONG_ART -> message.payloadId?.let { id -> message.art?.let { incomingArt[id] = it } }
            NearbyMessage.SONG_LYRICS -> message.payloadId?.let { id ->
                val synced = message.synced
                val plain = message.lyrics ?: synced?.let(::lrcToPlainText)
                if (plain != null) incomingLyrics[id] = LyricsDomain(lyrics = plain, synced = synced)
            }
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
        if (bytes.size > MAX_MESSAGE_BYTES) return // e.g. very long lyrics: the song goes without them
        client.sendPayload(endpointId, Payload.fromBytes(bytes))
    }

    // ---------------------- helpers ----------------------

    private fun updateFriend(endpointId: String, change: (ConnectedFriend) -> ConnectedFriend) {
        _state.update { state ->
            state.copy(friends = state.friends.map { if (it.endpointId == endpointId) change(it) else it })
        }
    }

    private fun removeFriend(endpointId: String) {
        party.onFriendDisconnected(endpointId)
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

        const val KEY_LET_SAVE = "let_friends_save"

        /** a sent song's picture: this many pixels at most, and short enough for one message */
        const val ART_SIZE = 300
        const val MAX_ART_CHARS = 28_000

        /** this much of a song is enough to start playing it (the rest streams in) */
        const val READY_BYTES = 64 * 1024L

        /** Nearby's limit for one message is 32 KB */
        const val MAX_MESSAGE_BYTES = 32_000
    }
}
