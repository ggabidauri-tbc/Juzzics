package com.example.juzzics.features.nearby.data

import com.example.juzzics.common.messages.AppMessage
import com.example.juzzics.common.messages.AppMessages
import android.content.ContentUris
import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import android.util.Base64
import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.PowerManager
import android.os.SystemClock
import android.provider.MediaStore
import android.provider.Settings
import androidx.core.content.edit
import com.example.juzzics.common.artwork.loadSongArtwork
import com.example.juzzics.features.lyrics.domain.model.LyricsDomain
import com.example.juzzics.features.lyrics.domain.model.lrcToPlainText
import com.example.juzzics.features.lyrics.domain.repo.LyricsRepo
import com.example.juzzics.features.musics.domain.model.MusicFileDomain
import com.example.juzzics.features.musics.domain.repo.MusicRepo
import com.example.juzzics.features.nearby.domain.BlendItem
import com.example.juzzics.features.nearby.domain.BumpState
import com.example.juzzics.features.nearby.domain.ChatMessage
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
import java.io.OutputStream

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
    /** this phone's lasting id, and the phones it paired with */
    private val book = FriendBook(context)
    private val alerts = RadarAlerts(context)

    private val _state = MutableStateFlow(
        NearbyState(
            deviceName = savedOrDefaultName(),
            letFriendsSave = prefs.getBoolean(KEY_LET_SAVE, true),
            rememberedPhones = book.count,
        )
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

    private val carDj = NearbyCarDj(
        player = player,
        scope = scope,
        broadcast = { message -> _state.value.friends.forEach { send(it.endpointId, message) } },
        onState = { open, queue -> _state.update { it.copy(carDj = open, djQueue = queue) } },
    )

    private val blendPlayer = NearbyBlendPlayer(
        player = player,
        scope = scope,
        requestSong = { endpointId, songId, key ->
            send(endpointId, NearbyMessage(NearbyMessage.STREAM_REQUEST, songId = songId, purpose = NearbyMessage.PURPOSE_BLEND, key = key))
        },
        findMine = { songId -> musicRepo.getAllLocalMusicFiles().getOrDefault(emptyList()).find { it.id == songId } },
        runAsync = { block -> scope.launch { block() } },
    )

    private val shoutOuts = ShoutOuts(context)

    /** positions, meeting points and "come to me" hop on through friends' phones */
    private val mesh = Mesh(myId = { book.myId }, myName = ::displayName)

    private val radar = FriendRadar(
        context = context,
        broadcast = ::broadcastMesh,
        sendTo = { endpointId, message -> send(endpointId, mesh.stamp(message)) },
        nameOf = ::friendName,
        onState = { radar -> _state.update { it.copy(radar = radar) } },
        onEvent = ::onRadarEvent,
    )

    /** says [message] to everyone connected, who pass it on (see [Mesh]) */
    private fun broadcastMesh(message: NearbyMessage) {
        val stamped = mesh.stamp(message)
        _state.value.friends.forEach { send(it.endpointId, stamped) }
    }

    private fun onRadarEvent(event: RadarEvent) {
        when (event) {
            is RadarEvent.PinSet -> {
                alerts.pin()
                AppMessages.show(AppMessage("${event.name} set a meeting point", "Show", { OpenRadarRequests.request() }))
            }
            is RadarEvent.CalledOver -> {
                val distance = event.distanceM?.let(::formatMeters)
                alerts.comeToMe(event.name, distance)
                AppMessages.show(
                    AppMessage(
                        "${event.name} asks you to come to them" + (distance?.let { " · $it" } ?: ""),
                        "Show",
                        { OpenRadarRequests.request() },
                        long = true,
                    )
                )
            }
        }
    }

    /** "meet here" at [lat] / [lon], for everyone */
    fun setMeetingPoint(lat: Double, lon: Double) = radar.setPin(lat, lon)

    fun clearMeetingPoint() = radar.clearPin()

    fun hideMeetingPoint(personId: String) = radar.hidePin(personId)

    fun showHiddenMeetingPoints() = radar.showHiddenPins()

    /** friends' phones buzz and point to this one */
    fun callFriendsOver() {
        when {
            _state.value.friends.isEmpty() -> AppMessages.show("Connect to a friend first")
            radar.callOver() -> AppMessages.show("Sent: friends' phones buzz and point to you")
            else -> AppMessages.show("Your position isn't known yet: wait for GPS, then try again")
        }
    }

    fun dismissComeToMe() = radar.dismissComeToMe()

    // ---------------------- checking on friends ----------------------

    /** alerts already given (per person and kind), so each one comes once */
    private val alerted = mutableSetOf<String>()

    fun setCheckMinutes(minutes: Int) {
        prefs.edit { putInt(KEY_CHECK_MINUTES, minutes) }
        _state.update { it.copy(checkMinutes = minutes) }
    }

    init {
        _state.update { it.copy(checkMinutes = prefs.getInt(KEY_CHECK_MINUTES, 30)) }
        scope.launch {
            while (true) {
                delay(CHECK_EVERY_MS)
                checkOnFriends()
            }
        }
    }

    /**
     * Low batteries, and (if on) someone sharing their location who hasn't moved, or hasn't been
     * heard of, for [NearbyState.checkMinutes]: each alert once, again only after it got better.
     */
    private fun checkOnFriends() {
        val state = _state.value
        val now = SystemClock.elapsedRealtime()
        val limit = state.checkMinutes * 60_000L

        fun once(key: String, bad: Boolean, alert: () -> Unit) {
            if (bad) {
                if (alerted.add(key)) alert()
            } else alerted -= key
        }

        // batteries: people sharing (also through friends), and connected friends
        val batteries = state.radar.people.map { (id, person) -> Triple(id, person.name, person.battery to person.charging) } +
                state.friends.filter { (it.phoneId ?: it.endpointId) !in state.radar.people }
                    .map { Triple(it.phoneId ?: it.endpointId, it.name, it.battery to it.charging) }
        batteries.forEach { (id, name, power) ->
            val (level, charging) = power
            if (level == null) return@forEach
            // (a few percent of room before it can warn again)
            val low = !charging && level <= LOW_BATTERY
            if (!low && level <= LOW_BATTERY + 5 && !charging) return@forEach
            once("battery/$id", low) {
                alerts.friendAlert("battery/$id", "$name's phone is at $level%", "When it dies, they're gone from the radar. Stay close or share a meeting point.")
            }
        }

        if (limit <= 0) return
        state.radar.people.forEach { (id, person) ->
            val silentFor = now - person.fix.atElapsedMs
            once("silent/$id", silentFor > limit) {
                alerts.friendAlert(
                    "silent/$id",
                    "No news from ${person.name} for ${silentFor / 60_000} min",
                    "Their phone may be off, or out of everyone's range. Tap to see where they were last."
                )
            }
            val stillFor = person.stillSinceMs?.let { now - it } ?: 0L
            // (only while their position keeps coming: otherwise it's "no news")
            once("still/$id", silentFor < limit && stillFor > limit) {
                alerts.friendAlert(
                    "still/$id",
                    "${person.name} hasn't moved for ${stillFor / 60_000} min",
                    "Check on them? Tap to see where they are."
                )
            }
        }
    }

    // ---------------------- group chat ----------------------

    /** the chat is on screen: nothing's unread */
    private var chatOpen = false

    fun setChatOpen(open: Boolean) {
        chatOpen = open
        if (open) {
            _state.update { it.copy(unreadChat = 0) }
            alerts.clearChat()
        }
    }

    /** to everyone (and on through their phones); [withLocation]: "I'm here" */
    fun sendChat(text: String, withLocation: Boolean) {
        val clean = text.trim().take(MAX_CHAT_CHARS)
        if (clean.isEmpty()) return
        val nobody = _state.value.friends.isEmpty()
        // nobody connected and nobody to wait for: nowhere it could go
        if (nobody && lost.isEmpty()) {
            AppMessages.show("Connect to a friend first")
            return
        }
        val here = if (withLocation) radar.myPosition() else null
        if (withLocation && here == null) AppMessages.show("Your position isn't known yet: sent without it")
        val stamped = mesh.stamp(
            NearbyMessage(NearbyMessage.CHAT, text = clean, lat = here?.lat, lon = here?.lon, accuracy = here?.accuracyM)
        )
        _state.value.friends.forEach { send(it.endpointId, stamped) }
        remember(stamped)
        addChat(
            ChatMessage(
                id = "${book.myId}/${stamped.seq}",
                from = "You",
                fromMe = true,
                text = clean,
                atMs = System.currentTimeMillis(),
                lat = here?.lat,
                lon = here?.lon,
                // a friend dropped out: it goes out as soon as they're back
                pending = nobody,
            )
        )
    }

    /**
     * the last half hour of chat (as sent), passed to anyone who (re)connects: what they missed
     * while out of range arrives then (what they already have is skipped, see [Mesh])
     */
    private val recentChat = ArrayDeque<NearbyMessage>()

    private fun remember(message: NearbyMessage) {
        recentChat.addLast(message)
        while (recentChat.size > MAX_CATCH_UP) recentChat.removeFirst()
    }

    /** [endpointId] just (re)connected: the chat it may have missed, and what waited for it */
    private fun catchUp(endpointId: String) {
        val since = System.currentTimeMillis() - CATCH_UP_MS
        recentChat.filter { (it.seq ?: 0L) >= since }.forEach { send(endpointId, it) }
        if (_state.value.chat.any { it.pending }) {
            _state.update { state -> state.copy(chat = state.chat.map { if (it.pending) it.copy(pending = false) else it }) }
        }
    }

    private fun receiveChat(endpointId: String, message: NearbyMessage) {
        val text = message.text?.trim()?.take(MAX_CHAT_CHARS)?.ifEmpty { null } ?: return
        val personId = message.origin ?: endpointId
        val name = message.originName ?: friendName(endpointId)
        val relayed = (message.hops ?: 0) > 0
        val lat = message.lat
        val lon = message.lon
        if (lat != null && lon != null) radar.notePosition(personId, name, lat, lon, message.accuracy, relayed)
        remember(message)
        // (the sender's counter is the time it was written: a message caught up later keeps its time)
        val now = System.currentTimeMillis()
        val written = message.seq?.takeIf { it in (now - CATCH_UP_MS * 2)..now + 60_000 } ?: now
        addChat(
            ChatMessage(
                id = "$personId/${message.seq ?: System.nanoTime()}",
                from = name,
                fromMe = false,
                text = text,
                atMs = written,
                personId = personId,
                lat = lat,
                lon = lon,
                relayed = relayed,
            )
        )
        acknowledge(message.origin, message.seq)
        notifyChat(name, text)
    }

    /** "arrived": a receipt back to the sender (through friends' phones if needed) */
    private fun acknowledge(origin: String?, seq: Long?) {
        if (origin == null || seq == null || origin == book.myId) return
        broadcastMesh(NearbyMessage(NearbyMessage.CHAT_ACK, ack = "$origin/$seq"))
    }

    /** a receipt: if it's for one of ours, who has it now */
    private fun receiveAck(endpointId: String, message: NearbyMessage) {
        val id = message.ack ?: return
        if (!id.startsWith("${book.myId}/")) return
        val who = message.origin ?: phoneIds[endpointId] ?: endpointId
        val name = message.originName ?: friendName(endpointId)
        _state.update { state ->
            state.copy(chat = state.chat.map {
                if (it.id == id && it.fromMe && who !in it.deliveredTo) it.copy(deliveredTo = it.deliveredTo + (who to name), pending = false)
                else it
            })
        }
    }

    /**
     * ours that nobody confirmed yet (sent into a connection that was just breaking, say):
     * sent again to whoever is connected (a copy someone already has is skipped there)
     */
    private fun resendUnconfirmed() {
        val friends = _state.value.friends.map { it.endpointId }
        if (friends.isEmpty()) return
        val unconfirmed = _state.value.chat.filter { it.fromMe && it.photoPath == null && it.deliveredTo.isEmpty() }.map { it.id }.toSet()
        if (unconfirmed.isEmpty()) return
        val since = System.currentTimeMillis() - CATCH_UP_MS
        recentChat
            .filter { it.origin == book.myId && (it.seq ?: 0L) >= since && "${book.myId}/${it.seq}" in unconfirmed }
            .forEach { message -> friends.forEach { send(it, message) } }
    }

    /** unread + a notification, unless the chat is on screen */
    private fun notifyChat(name: String, text: String) {
        if (chatOpen) return
        _state.update { it.copy(unreadChat = it.unreadChat + 1) }
        // a real notification also while the app is open (only not with the chat on screen)
        alerts.chat(name, text, _state.value.unreadChat)
    }

    // ---------------------- photo drop ----------------------

    /** photos sent and received (this session; old ones are cleared when the app starts) */
    private val photosDir = File(context.cacheDir, "nearby_photos").apply { mkdirs() }

    init {
        scope.launch(Dispatchers.IO) {
            val old = System.currentTimeMillis() - PHOTO_KEEP_MS
            photosDir.listFiles()?.filter { it.lastModified() < old }?.forEach { it.delete() }
        }
    }

    /** a photo to everyone (and on through their phones), shown in the group chat */
    fun sendPhoto(uri: Uri, caption: String) {
        val friends = _state.value.friends.map { it.endpointId }
        if (friends.isEmpty()) {
            AppMessages.show("Connect to a friend first")
            return
        }
        scope.launch {
            // smaller (a phone photo is 3-10 MB; this is a few hundred KB and still sharp on a phone)
            val photo = withContext(Dispatchers.IO) { runCatching { shrinkPhoto(uri) }.getOrNull() }
            if (photo == null) {
                AppMessages.show("Couldn't open that photo")
                return@launch
            }
            val clean = caption.trim().take(MAX_CHAT_CHARS)
            val info = mesh.stamp(
                NearbyMessage(
                    NearbyMessage.FILE_INFO,
                    sizeBytes = photo.length(),
                    purpose = NearbyMessage.PURPOSE_PHOTO,
                    text = clean.ifEmpty { null },
                )
            )
            sendFileTo(photo, info, _state.value.friends.map { it.endpointId })
            addChat(
                ChatMessage(
                    id = "${book.myId}/${info.seq}",
                    from = "You",
                    fromMe = true,
                    text = clean,
                    atMs = System.currentTimeMillis(),
                    photoPath = photo.absolutePath,
                )
            )
        }
    }

    /** decodes [uri] upright, at most [MAX_PHOTO_PX] on the long side, as a JPEG file */
    private fun shrinkPhoto(uri: Uri): File {
        val bitmap: Bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // (turns camera photos upright by itself)
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                val scale = minOf(1f, MAX_PHOTO_PX.toFloat() / maxOf(info.size.width, info.size.height))
                decoder.setTargetSize((info.size.width * scale).toInt().coerceAtLeast(1), (info.size.height * scale).toInt().coerceAtLeast(1))
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_PHOTO_PX) sample *= 2
            val decoded = context.contentResolver.openInputStream(uri).use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: error("not an image")
            val scale = minOf(1f, MAX_PHOTO_PX.toFloat() / maxOf(decoded.width, decoded.height))
            if (scale < 1f) Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt(), (decoded.height * scale).toInt(), true)
            else decoded
        }
        val file = File(photosDir, "me_${System.currentTimeMillis()}.jpg")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, PHOTO_QUALITY, it) }
        return file
    }

    /** a photo arrived: into the chat, and on to friends out of the sender's range */
    private fun receivePhoto(endpointId: String, file: File, info: NearbyMessage) {
        val origin = info.origin
        // our own come back, or the same photo a second way
        if (origin == book.myId || (origin != null && !firstTime("photo/$origin/${info.seq}"))) {
            file.delete()
            return
        }
        val photo = File(photosDir, "${origin ?: endpointId}_${info.seq ?: System.nanoTime()}.jpg")
        if (!file.renameTo(photo)) {
            runCatching { file.copyTo(photo, overwrite = true) }
            file.delete()
        }
        mesh.forwarded(info)?.let { onward ->
            val targets = _state.value.friends.map { it.endpointId }.filter { it != endpointId }
            if (targets.isNotEmpty()) sendFileTo(photo, onward.copy(sizeBytes = photo.length()), targets)
        }
        val name = info.originName ?: friendName(endpointId)
        val caption = info.text?.trim()?.take(MAX_CHAT_CHARS).orEmpty()
        addChat(
            ChatMessage(
                id = "${origin ?: endpointId}/${info.seq ?: System.nanoTime()}",
                from = name,
                fromMe = false,
                text = caption,
                atMs = System.currentTimeMillis(),
                personId = origin,
                relayed = (info.hops ?: 0) > 0,
                photoPath = photo.absolutePath,
            )
        )
        acknowledge(origin, info.seq)
        notifyChat(name, if (caption.isEmpty()) "📷 Photo" else "📷 $caption")
    }

    private fun addChat(message: ChatMessage) {
        // in order of writing (a caught-up message can be older than the last one shown)
        _state.update { state -> state.copy(chat = (state.chat + message).sortedBy { it.atMs }.takeLast(MAX_CHAT_MESSAGES)) }
    }

    /** friend radar: where this phone points (degrees from north), while the radar is open */
    val radarHeading: StateFlow<Float?> = radar.heading

    private var sharingTimer: Job? = null
    private var aloneTimer: Job? = null

    /**
     * friend radar: connected friends see where this phone is, also with the screen off
     * (a notification shows it). [forMs]: stops by itself after that long; null = until turned off
     */
    fun setLocationSharing(on: Boolean, forMs: Long? = null) {
        sharingTimer?.cancel()
        aloneTimer?.cancel()
        if (!on) {
            radar.setSharing(false)
            // (the background service drops its location part by itself)
            return
        }
        radar.setSharing(true, untilMs = forMs?.let { System.currentTimeMillis() + it })
        NearbyService.start(context)
        if (forMs != null) {
            sharingTimer = scope.launch {
                delay(forMs)
                setLocationSharing(false)
                AppMessages.show("Stopped sharing your location (time's up)")
            }
        }
    }

    /** forgets everyone's trails (friend map) */
    fun clearTrails() = radar.clearTrails()

    /** friend radar on screen: GPS + compass on */
    fun setRadarVisible(visible: Boolean) = radar.setVisible(visible)

    private val liveMic = LiveMic(context)
    /** live voice streams announced by MIC_START: payload id to sample rate */
    private val micStreams = mutableMapOf<Long, Int>()
    /** the ones of them that are walkie-talkie messages (their MIC_START: who talks, relayed or not) */
    private val walkieStreams = mutableMapOf<Long, NearbyMessage>()
    /** who's singing through this phone */
    private var singerId: String? = null

    /** names of phones we're (becoming) connected to */
    private val names = mutableMapOf<String, String>()
    /** endpoint id to the phone's lasting id */
    private val phoneIds = mutableMapOf<String, String>()
    /** connections accepted without comparing codes (a paired phone): not friends until they prove it */
    private val autoAccepted = mutableSetOf<String>()
    /** the question each unproven phone has to answer */
    private val myNonces = mutableMapOf<String, String>()
    private val probation = mutableMapOf<String, Job>()
    /** paired friends who dropped out of range (phone id to when), to reconnect to */
    private val lost = mutableMapOf<String, Long>()
    /** said BYE: disconnecting on purpose, don't look for them */
    private val leaving = mutableSetOf<String>()
    /**
     * paired phones that failed to prove who they are: the next connection compares codes (kept
     * in memory only, so a stranger copying an id can't erase a real pairing)
     */
    private val compareCodes = mutableSetOf<String>()
    /** reconnect requests on their way (phone ids) */
    private val reconnecting = mutableSetOf<String>()
    private var reconnectJob: Job? = null
    private val reconnectWakeLock: PowerManager.WakeLock =
        (context.getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "juzzics:reconnect")
            .apply { setReferenceCounted(false) }
    /** phones the user tapped Connect on (their codes dialog is expected) */
    private val userRequested = mutableSetOf<String>()
    /** when the user last looked for friends (a friend's request can come a bit later) */
    private var lastSearchAt = 0L
    /** the user turned "Visible to friends" off: reconnecting only looks, it doesn't advertise */
    private var hiddenByUser = false
    /** discovery wanted by the user ("Find friends") / by reconnecting, and running */
    private var userSearching = false
    private var reconnectScanning = false
    private var discovering = false
    /** walkie-talkie: who's talking through this phone, and the talk time limit */
    private var talkerId: String? = null
    private var talkLimit: Job? = null
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
        hiddenByUser = !on
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
        client.startAdvertising(advertisedName(), SERVICE_ID, connectionCallback, options)
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
        userSearching = true
        lastSearchAt = System.currentTimeMillis()
        _state.update { it.copy(found = emptyList(), error = null, searching = true) }
        stopSearchJob?.cancel()
        stopSearchJob = scope.launch {
            delay(SEARCH_DURATION_MS)
            stopSearching()
        }
        if (discovering) {
            // already looking (reconnecting): start over, so phones seen before show up in the list
            client.stopDiscovery()
            discovering = false
        }
        refreshDiscovery()
    }

    fun stopSearching() {
        stopSearchJob?.cancel()
        userSearching = false
        _state.update { it.copy(searching = false) }
        refreshDiscovery()
    }

    /** discovery runs while the user searches or a lost friend is being looked for */
    private fun refreshDiscovery() {
        val wanted = userSearching || reconnectScanning || bumping
        if (wanted && !discovering && !discoveryInFlight) {
            discoveryInFlight = true
            val options = DiscoveryOptions.Builder().setStrategy(STRATEGY).build()
            client.startDiscovery(SERVICE_ID, discoveryCallback, options)
                .addOnSuccessListener { discovering = true }
                .addOnFailureListener { e ->
                    if (e.statusCode() == ConnectionsStatusCodes.STATUS_ALREADY_DISCOVERING) {
                        discovering = true
                    } else if (userSearching) {
                        userSearching = false
                        stopSearchJob?.cancel()
                        _state.update { it.copy(searching = false, error = "Couldn't search. ${e.explain()}") }
                    }
                }
                .addOnCompleteListener {
                    discoveryInFlight = false
                    // turned off meanwhile
                    if (!userSearching && !reconnectScanning && !bumping && discovering) refreshDiscovery()
                }
        } else if (!wanted && discovering) {
            client.stopDiscovery()
            discovering = false
        }
    }

    // ---------------------- bump to connect ----------------------

    /** "bump to connect" is open */
    private var bumping = false
    /** bumping phones connected to (or connecting), not friends until the bump matches */
    private val bumpCandidates = mutableSetOf<String>()
    private val bumpConnected = mutableSetOf<String>()
    /** when each candidate said it felt a bump (this phone's clock, when it arrived) */
    private val remoteBumps = mutableMapOf<String, Long>()
    private var myBumpAt = 0L
    private var bumpTimeout: Job? = null
    private val bumpDetector = BumpDetector(context) { at -> scope.launch { onLocalBump(at) } }

    /**
     * Opens "bump to connect": this phone shows it's bumping, finds other bumping phones and
     * connects to them quietly (nothing but bumps goes through). Tapping two phones together
     * makes both feel a jolt at the same moment: those two become friends, no codes.
     */
    fun startBump() {
        if (bumping) return
        bumping = true
        _state.update { it.copy(bump = BumpState(noSensor = !bumpDetector.available)) }
        bumpDetector.start()
        restartAdvertising()
        // a fresh search: phones seen before (not bumping then) show up again
        if (discovering) {
            client.stopDiscovery()
            discovering = false
        }
        refreshDiscovery()
        bumpTimeout?.cancel()
        bumpTimeout = scope.launch {
            delay(BUMP_MODE_MS)
            stopBump()
        }
    }

    fun stopBump() {
        if (!bumping) return
        bumping = false
        bumpTimeout?.cancel()
        bumpDetector.stop()
        // the phones that weren't bumped: goodbye
        bumpCandidates.toList().forEach { runCatching { client.disconnectFromEndpoint(it) } }
        bumpCandidates.clear()
        bumpConnected.clear()
        remoteBumps.clear()
        _state.update { it.copy(bump = null) }
        restartAdvertising()
        refreshDiscovery()
    }

    /** the "tap together" button (no motion sensor, or a gentle bump that wasn't felt) */
    fun tapBump() = onLocalBump(SystemClock.elapsedRealtime())

    private fun onLocalBump(at: Long) {
        if (!bumping) return
        myBumpAt = at
        bumpConnected.forEach { send(it, NearbyMessage(NearbyMessage.BUMP)) }
        // one of them bumped just before us
        remoteBumps.filterValues { at - it in 0..BUMP_WINDOW_MS }.keys.firstOrNull()?.let(::bumped)
    }

    private fun onRemoteBump(endpointId: String) {
        val now = SystemClock.elapsedRealtime()
        remoteBumps[endpointId] = now
        if (now - myBumpAt in 0..BUMP_WINDOW_MS) bumped(endpointId)
    }

    /** the same bump on both phones: friends now (paired, they reconnect by themselves later too) */
    private fun bumped(endpointId: String) {
        if (!bumpCandidates.remove(endpointId)) return
        bumpConnected -= endpointId
        remoteBumps -= endpointId
        addFriend(endpointId, paired = true)
        val name = friendName(endpointId)
        alerts.pin()
        _state.update { it.copy(bump = it.bump?.copy(connectedTo = name)) }
        updateBumpState()
        AppMessages.show("Connected to $name")
    }

    private fun connectForBump(endpointId: String, phoneId: String) {
        if (endpointId in bumpCandidates) return
        scope.launch {
            // one of the two asks (the other a bit later, if nothing happened)
            if (book.myId > phoneId) delay(3_000)
            if (!bumping || endpointId in bumpCandidates || _state.value.friends.any { it.phoneId == phoneId }) return@launch
            client.requestConnection(FriendBook.encodeName(displayName(), book.myId, bump = true), endpointId, connectionCallback)
        }
    }

    private fun updateBumpState() {
        _state.update { state -> state.copy(bump = state.bump?.copy(ready = bumpConnected.size)) }
    }

    /** advertising again under the current name (bumping or not) */
    private fun restartAdvertising() {
        if (hiddenByUser && !bumping) return
        client.stopAdvertising()
        advertisingInFlight = false
        startAdvertising()
    }

    // ---------------------- reconnecting ----------------------

    /**
     * Paired friends who dropped out (walked out of range): look for them in bursts (20 s of
     * searching a minute, battery), for [RECONNECT_WINDOW_MS], or as long as location sharing
     * is on (a hike). A found one reconnects without codes.
     */
    private fun updateReconnect() {
        val now = System.currentTimeMillis()
        if (!_state.value.radar.sharing) lost.entries.removeAll { now - it.value > RECONNECT_WINDOW_MS }
        val connected = _state.value.friends.mapNotNull { it.phoneId }.toSet()
        lost.keys.removeAll { it in connected || book.known(it) == null }
        _state.update { state -> state.copy(reconnecting = lost.keys.mapNotNull { book.known(it)?.name }) }
        if (lost.isEmpty()) {
            runCatching { if (reconnectWakeLock.isHeld) reconnectWakeLock.release() }
            reconnectJob?.cancel()
            reconnectJob = null
            if (reconnectScanning) {
                reconnectScanning = false
                refreshDiscovery()
            }
            return
        }
        // they have to find us too
        if (!_state.value.sharing && !hiddenByUser) startAdvertising()
        // the search bursts keep running with the screen off (timers would stall in deep sleep)
        runCatching { reconnectWakeLock.acquire(if (_state.value.radar.sharing) 60 * 60 * 1000L else RECONNECT_WINDOW_MS) }
        if (reconnectJob?.isActive == true) return
        reconnectJob = scope.launch {
            while (lost.isNotEmpty()) {
                reconnectScanning = true
                refreshDiscovery()
                delay(RECONNECT_SCAN_MS)
                // right after a drop (Wi-Fi turned off, a few steps too far) they're most likely
                // still close: look almost without a break for the first 2 minutes
                val justLost = System.currentTimeMillis() - (lost.values.maxOrNull() ?: 0L) < FAST_RECONNECT_MS
                // (a short stop in between: a new search reports phones seen before again)
                reconnectScanning = false
                refreshDiscovery()
                delay(if (justLost) 2_000L else RECONNECT_PAUSE_MS)
                updateReconnect()
            }
        }
    }

    /** a lost friend showed up: one of the two phones asks (the other a bit later, if nothing happens) */
    private fun reconnectTo(endpointId: String, phoneId: String) {
        if (phoneId in reconnecting) return
        reconnecting += phoneId
        scope.launch {
            if (book.myId > phoneId) delay(RECONNECT_SECOND_ASK_MS)
            if (phoneId in lost) {
                client.requestConnection(advertisedName(), endpointId, connectionCallback)
                    .addOnCompleteListener { reconnecting -= phoneId }
            } else reconnecting -= phoneId
        }
    }

    /** forgets every paired phone (they have to compare codes again) */
    fun forgetRememberedPhones() {
        book.forgetAll()
        lost.clear()
        updateReconnect()
        _state.update { it.copy(rememberedPhones = 0) }
    }

    /** a reconnected phone must answer a question only the pair's secret answers, soon */
    private fun startProbation(endpointId: String) {
        val nonce = book.newNonce()
        myNonces[endpointId] = nonce
        send(endpointId, NearbyMessage(NearbyMessage.HELLO, phoneId = book.myId, nonce = nonce))
        probation[endpointId] = scope.launch {
            delay(PROOF_TIMEOUT_MS)
            if (_state.value.friends.none { it.endpointId == endpointId }) {
                // no answer: drop the connection (and keep the pairing: it may just be a slow phone)
                client.disconnectFromEndpoint(endpointId)
                cleanUpEndpoint(endpointId)
            }
        }
    }

    private fun cleanUpEndpoint(endpointId: String) {
        userRequested -= endpointId
        if (bumpCandidates.remove(endpointId)) {
            bumpConnected -= endpointId
            remoteBumps -= endpointId
            updateBumpState()
        }
        myNonces -= endpointId
        probation.remove(endpointId)?.cancel()
        autoAccepted -= endpointId
        leaving -= endpointId
        phoneIds -= endpointId
        names -= endpointId
    }

    fun connect(device: NearbyDevice) {
        if (_state.value.pending != null) return // already pairing with someone
        userRequested += device.endpointId
        client.requestConnection(advertisedName(), device.endpointId, connectionCallback)
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

    /** everyone, on purpose (the notification's "Disconnect"): nobody reconnects */
    fun disconnectAll() {
        _state.value.friends.forEach { disconnect(it.endpointId) }
        lost.clear()
        updateReconnect()
        if (_state.value.radar.sharing) setLocationSharing(false)
    }

    fun disconnect(endpointId: String) {
        // on purpose: neither phone looks for the other afterwards
        send(endpointId, NearbyMessage(NearbyMessage.BYE))
        leaving += endpointId
        phoneIds[endpointId]?.let { lost -= it }
        radar.forget(phoneIds[endpointId] ?: endpointId)
        scope.launch {
            delay(300) // the goodbye goes first
            client.disconnectFromEndpoint(endpointId)
            removeFriend(endpointId)
        }
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

    /** Car DJ: connected friends may add songs to this phone's queue */
    fun setCarDj(on: Boolean) = carDj.setOpen(on)

    /** Car DJ (their phone): adds one of their own songs to their queue */
    fun queueOnFriend(endpointId: String, songId: Long) =
        send(endpointId, NearbyMessage(NearbyMessage.QUEUE_ADD, songId = songId))

    /** Car DJ (their phone): sends one of this phone's songs into their queue */
    fun queueMySongOnFriend(endpointId: String, songId: Long) =
        sendSongFile(endpointId, songId, askedByFriend = false, purpose = NearbyMessage.PURPOSE_QUEUE)

    /** plays a blend from [start]: friends' songs stream in one at a time */
    fun playBlend(mix: List<BlendItem>, start: Int) = blendPlayer.play(mix, start)

    /** plays some of this phone's own songs (e.g. the ones shared with friends) */
    fun playMine(songIds: List<Long>, start: Int, source: String) {
        scope.launch {
            val all = musicRepo.getAllLocalMusicFiles().getOrDefault(emptyList()).associateBy { it.id }
            val songs = songIds.mapNotNull { all[it] }
            if (songs.isNotEmpty()) player.playQueue(songs, start.coerceIn(songs.indices), source)
        }
    }

    /** sing-along: this phone's mic plays live on [endpointId]'s phone, over its music */
    fun startSinging(endpointId: String) {
        stopSinging()
        val voice = liveMic.startSinging()
        if (voice == null) {
            _state.update { it.copy(error = "Couldn't use the microphone") }
            return
        }
        val payload = Payload.fromStream(voice)
        send(endpointId, NearbyMessage(NearbyMessage.MIC_START, payloadId = payload.id, sampleRate = LiveMic.SAMPLE_RATE))
        client.sendPayload(endpointId, payload)
            .addOnFailureListener { e -> stopSinging(tellThem = false); _state.update { it.copy(error = "Couldn't reach their phone. ${e.explain()}") } }
        _state.update { it.copy(singingTo = endpointId) }
        // keeps the microphone working with the screen off / locked
        SingingService.start(context, friendName(endpointId))
    }

    fun stopSinging(tellThem: Boolean = true) {
        val to = _state.value.singingTo ?: return
        liveMic.stopSinging()
        SingingService.stop(context)
        if (tellThem) send(to, NearbyMessage(NearbyMessage.MIC_STOP))
        _state.update { it.copy(singingTo = null) }
    }

    /** on the phone playing the voice: turn the singer's mic off */
    fun stopSinger() {
        singerId?.let { send(it, NearbyMessage(NearbyMessage.MIC_STOP)) }
        liveMic.stopListening()
    }

    fun setMicGain(gain: Float) {
        liveMic.gain = gain
        _state.update { it.copy(micGain = gain) }
    }

    private fun listenToSinger(endpointId: String, payload: Payload, sampleRate: Int) {
        val voice = payload.asStream()?.asInputStream() ?: return
        // singing takes over from a walkie-talkie message (whose end then isn't reported)
        endTalker()
        singerId = endpointId
        _state.update { it.copy(singer = friendName(endpointId)) }
        liveMic.listen(voice, sampleRate) {
            // (on the audio thread)
            scope.launch {
                if (singerId == endpointId) {
                    singerId = null
                    _state.update { it.copy(singer = null) }
                }
            }
        }
    }

    // ---------------------- walkie-talkie ----------------------

    /** hold to talk: this phone's mic goes live to every connected friend, over their music */
    fun startTalking() {
        val friends = _state.value.friends.map { it.endpointId }
        if (friends.isEmpty() || _state.value.talking || _state.value.singingTo != null) return
        val voice = liveMic.startSinging()
        if (voice == null) {
            _state.update { it.copy(error = "Couldn't use the microphone") }
            return
        }
        val payload = Payload.fromStream(voice)
        // who's talking (and which talk), so friends can pass it on to phones out of our range
        val start = mesh.stamp(
            NearbyMessage(NearbyMessage.MIC_START, payloadId = payload.id, sampleRate = LiveMic.SAMPLE_RATE, purpose = NearbyMessage.PURPOSE_WALKIE)
        )
        friends.forEach { send(it, start) }
        client.sendPayload(friends, payload).addOnFailureListener { stopTalking() }
        _state.update { it.copy(talking = true) }
        talkLimit = scope.launch {
            delay(MAX_TALK_MS)
            stopTalking()
        }
    }

    /** let go: the voice stream ends, their phones beep */
    fun stopTalking() {
        talkLimit?.cancel()
        if (!_state.value.talking) return
        liveMic.stopSinging()
        _state.update { it.copy(talking = false) }
    }

    /** a friend talks: beep, music quieter, their voice */
    private fun listenToTalker(endpointId: String, payload: Payload, sampleRate: Int, info: NearbyMessage?) {
        val voice = payload.asStream()?.asInputStream() ?: return
        // this phone's own voice come back, or the same talk by a second way: once is enough
        val origin = info?.origin
        if (origin == book.myId || (origin != null && !firstTime("$origin/${info.seq}"))) {
            runCatching { voice.close() }
            return
        }
        // a friend singing through this phone, or another talking, has the speaker
        if (singerId != null || (talkerId != null && talkerId != endpointId)) {
            runCatching { voice.close() }
            return
        }
        talkerId = endpointId
        val name = info?.originName ?: friendName(endpointId)
        _state.update { it.copy(talker = if ((info?.hops ?: 0) > 0) "$name (via friends)" else name) }
        alerts.talkStart()
        player.duck(true)
        liveMic.listen(voice, sampleRate, forward = info?.let { relayVoice(endpointId, it) }) {
            // (on the audio thread)
            scope.launch { if (talkerId == endpointId) endTalker() }
        }
    }

    /** voices (walkie-talkie, shout-outs) heard, by who + which: each plays once */
    private val heardVoices = LinkedHashSet<String>()

    private fun firstTime(key: String): Boolean {
        if (!heardVoices.add(key)) return false
        if (heardVoices.size > 200) heardVoices.remove(heardVoices.first())
        return true
    }

    /**
     * passes a friend's live voice on to the other friends (the ones out of the talker's range
     * hear it through this phone): a new live stream to them, fed as the voice arrives.
     * null: nobody to pass it to, or it went far enough
     */
    private fun relayVoice(fromEndpoint: String, info: NearbyMessage): OutputStream? {
        val onward = mesh.forwarded(info) ?: return null
        val targets = _state.value.friends.map { it.endpointId }.filter { it != fromEndpoint }
        if (targets.isEmpty()) return null
        return runCatching {
            val (readSide, writeSide) = ParcelFileDescriptor.createPipe().let { it[0] to it[1] }
            val payload = Payload.fromStream(readSide)
            val start = onward.copy(payloadId = payload.id)
            targets.forEach { send(it, start) }
            client.sendPayload(targets, payload)
            ParcelFileDescriptor.AutoCloseOutputStream(writeSide)
        }.getOrNull()
    }

    /** the walkie-talkie voice is over: music back up */
    private fun endTalker() {
        if (talkerId == null) return
        talkerId = null
        _state.update { it.copy(talker = null) }
        player.duck(false)
        alerts.talkEnd()
    }

    fun startShoutOut() {
        if (_state.value.friends.isEmpty()) return
        if (shoutOuts.startRecording()) _state.update { it.copy(recordingShoutOut = true) }
        else _state.update { it.copy(error = "Couldn't use the microphone") }
    }

    /** [send] false: cancelled (e.g. the finger slid away) */
    fun stopShoutOut(send: Boolean) {
        _state.update { it.copy(recordingShoutOut = false) }
        if (!send) {
            shoutOuts.cancelRecording()
            return
        }
        val voice = shoutOuts.stopRecording() ?: return
        // who said it (and which one), so friends pass it on to phones out of our range
        val info = mesh.stamp(
            NearbyMessage(NearbyMessage.FILE_INFO, sizeBytes = voice.length(), purpose = NearbyMessage.PURPOSE_SHOUTOUT)
        )
        sendFileTo(voice, info, _state.value.friends.map { it.endpointId })
        // the open streams keep reading it; the file itself can go
        scope.launch {
            delay(60_000)
            voice.delete()
        }
    }

    /** sends one of this phone's songs to play it on theirs */
    fun sendToFriend(endpointId: String, songId: Long) = sendSongFile(endpointId, songId, askedByFriend = false)

    /** this phone's songs, to pick one to send */
    suspend fun mySongs(): List<RemoteSong> =
        musicRepo.getAllLocalMusicFiles().getOrDefault(emptyList()).map { it.toRemote() }

    /**
     * a friend asked for one of this phone's songs, or picked one to send.
     * [purpose] / [key] go back with it (e.g. a blend matches the answer to its request)
     */
    private fun sendSongFile(
        endpointId: String,
        songId: Long,
        askedByFriend: Boolean,
        purpose: String? = null,
        key: Long? = null,
    ) {
        scope.launch {
            val song = musicRepo.getAllLocalMusicFiles().getOrDefault(emptyList()).find { it.id == songId }
            if (song == null || !sendSongFile(endpointId, song, purpose = purpose, key = key)) {
                if (askedByFriend) send(endpointId, NearbyMessage(NearbyMessage.FILE_FAILED, songId = songId, purpose = purpose, key = key))
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
    private suspend fun sendSongFile(
        endpointId: String,
        song: MusicFileDomain,
        partyKey: Long? = null,
        purpose: String? = partyKey?.let { NearbyMessage.PURPOSE_PARTY },
        key: Long? = null,
    ): Boolean {
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
                purpose = purpose,
                partyKey = partyKey,
                key = key,
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
        if (message.purpose != NearbyMessage.PURPOSE_SHOUTOUT && message.purpose != NearbyMessage.PURPOSE_PHOTO) addTransfer(
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
        if (info.purpose == NearbyMessage.PURPOSE_PHOTO) {
            // a photo: shown once it's all here
            if (!complete) return
            song.announced = true
            receivePhoto(song.endpointId, File(path), info)
            return
        }
        if (info.purpose == NearbyMessage.PURPOSE_SHOUTOUT) {
            // a voice message: small, played once it's all here, not kept
            if (!complete) return
            song.announced = true
            val file = File(path)
            val origin = info.origin
            // the same shout-out a second way (or our own come back): once is enough
            if (origin == book.myId || (origin != null && !firstTime("$origin/${info.seq}"))) {
                file.delete()
                return
            }
            // on to friends out of the speaker's range (opened before playing deletes the file)
            mesh.forwarded(info)?.let { onward ->
                val targets = _state.value.friends.map { it.endpointId }.filter { it != song.endpointId }
                if (targets.isNotEmpty()) sendFileTo(file, onward.copy(sizeBytes = file.length()), targets)
            }
            val name = info.originName ?: friendName(song.endpointId)
            playShoutOut(file, if ((info.hops ?: 0) > 0) "$name (via friends)" else name)
            return
        }
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
            val key = info.key
            val music = received.toMusicFile(receivedSong)
            if (info.purpose == NearbyMessage.PURPOSE_PARTY && partyKey != null) {
                party.onSongReady(partyKey, music)
            } else if (info.purpose == NearbyMessage.PURPOSE_BLEND && key != null) {
                blendPlayer.onSongReady(key, music)
            } else if (info.purpose == NearbyMessage.PURPOSE_QUEUE) {
                // Car DJ turned off meanwhile: it just stays in "Songs friends sent"
                if (carDj.open) carDj.add(music, receivedSong.from)
            } else {
                player.playQueue(listOf(received.toMusicFile(receivedSong)), 0, source = "From ${receivedSong.from}")
                if (asked) OpenPlayerRequests.request()
            }
        }
    }

    /** a file (shout-out, photo) to [targets], each its own stream (the file can be deleted meanwhile) */
    private fun sendFileTo(voice: File, info: NearbyMessage, targets: List<String>) {
        targets.forEach { endpointId ->
            runCatching {
                val payload = Payload.fromStream(ParcelFileDescriptor.open(voice, ParcelFileDescriptor.MODE_READ_ONLY))
                send(endpointId, info.copy(payloadId = payload.id))
                client.sendPayload(endpointId, payload)
            }
        }
    }

    private fun playShoutOut(voice: File, from: String) {
        _state.update { it.copy(shoutOutFrom = from) }
        shoutOuts.play(voice) { _state.update { it.copy(shoutOutFrom = null) } }
    }

    private fun receiveFailed(song: IncomingSong) {
        song.failed = true
        requestedPayloads -= song.payloadId
        incomingArt -= song.payloadId
        incomingLyrics -= song.payloadId
        removeTransfer(inKey(song.payloadId))
        scope.launch { received.remove(song.songId) }
        song.path?.let { File(it).delete() }
        val what = when (song.info?.purpose) {
            NearbyMessage.PURPOSE_PHOTO -> "A photo"
            NearbyMessage.PURPOSE_SHOUTOUT -> "A shout-out"
            else -> "\"${song.info?.title ?: "The song"}\""
        }
        _state.update { it.copy(error = "$what stopped half-way. Stay closer together and try again.") }
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
            val (name, phoneId) = FriendBook.decodeName(info.endpointName)
            // both in "bump to connect": connect right away, the bump decides who stays
            if (bumping && phoneId != null && FriendBook.isBumping(info.endpointName) &&
                _state.value.friends.none { it.phoneId == phoneId }
            ) {
                connectForBump(endpointId, phoneId)
                return
            }
            if (phoneId != null) {
                // already connected (e.g. it lost and found us again)
                if (_state.value.friends.any { it.phoneId == phoneId }) return
                // a friend who dropped out: back together without codes
                if (phoneId in lost) {
                    reconnectTo(endpointId, phoneId)
                    return
                }
            }
            if (!userSearching) return
            _state.update { state ->
                if (state.found.any { it.endpointId == endpointId }) state
                else state.copy(found = state.found + NearbyDevice(endpointId, name))
            }
        }

        override fun onEndpointLost(endpointId: String) {
            _state.update { state -> state.copy(found = state.found.filterNot { it.endpointId == endpointId }) }
        }
    }

    private val connectionCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            val (name, phoneId) = FriendBook.decodeName(info.endpointName)
            names[endpointId] = name
            phoneId?.let { phoneIds[endpointId] = it }
            // both in "bump to connect": no codes, the bump is the proof (until then: nothing but bumps)
            // (also a phone paired before: one of the two may have forgotten the other, the bump pairs them anew)
            if (bumping && phoneId != null && FriendBook.isBumping(info.endpointName)) {
                bumpCandidates += endpointId
                client.acceptConnection(endpointId, payloadCallback)
                return
            }
            if (phoneId != null && book.known(phoneId) != null && phoneId !in compareCodes) {
                // paired before: no codes; it proves who it is once connected (startProbation)
                autoAccepted += endpointId
                client.acceptConnection(endpointId, payloadCallback)
                return
            }
            // codes to compare only when this phone is looking for friends (or asked this one):
            // never out of the blue (a phone that remembers us while we forgot it, a stranger)
            val expected = userSearching || endpointId in userRequested ||
                    System.currentTimeMillis() - lastSearchAt < EXPECT_REQUESTS_MS
            if (!expected) {
                rejectedByMe += endpointId
                client.rejectConnection(endpointId)
                return
            }
            _state.update {
                it.copy(
                    pending = PendingConnection(
                        endpointId = endpointId,
                        name = name,
                        code = info.authenticationDigits,
                        incoming = info.isIncomingConnection,
                    )
                )
            }
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            _state.update { if (it.pending?.endpointId == endpointId) it.copy(pending = null) else it }
            val iRejected = rejectedByMe.remove(endpointId)
            if (endpointId in bumpCandidates) {
                // connected and waiting for the bump
                if (result.status.statusCode == ConnectionsStatusCodes.STATUS_OK) {
                    bumpConnected += endpointId
                    updateBumpState()
                } else cleanUpEndpoint(endpointId)
                return
            }
            val automatic = autoAccepted.remove(endpointId)
            if (automatic) {
                // a paired phone: a friend again once it answers our question
                if (result.status.statusCode == ConnectionsStatusCodes.STATUS_OK) startProbation(endpointId)
                else {
                    // it doesn't know us any more (it said no without asking anyone): stop trying
                    if (result.status.statusCode == ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED) {
                        phoneIds[endpointId]?.let {
                            book.forget(it)
                            lost -= it
                        }
                        _state.update { it.copy(rememberedPhones = book.count) }
                        updateReconnect()
                    }
                    cleanUpEndpoint(endpointId)
                }
                return
            }
            if (result.status.statusCode == ConnectionsStatusCodes.STATUS_OK) {
                addFriend(endpointId, paired = true)
            } else if (!iRejected) {
                val who = names.remove(endpointId) ?: "the other phone"
                val error = if (result.status.statusCode == ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED)
                    "$who didn't accept the connection" else "Couldn't connect to $who. Try again closer together."
                _state.update { it.copy(error = error) }
            }
        }

        override fun onDisconnected(endpointId: String) {
            if (_state.value.friends.any { it.endpointId == endpointId }) removeFriend(endpointId)
            else cleanUpEndpoint(endpointId)
        }
    }

    /**
     * [endpointId] is a friend now: codes compared ([paired], they get a pairing secret so they
     * reconnect by themselves later) or reconnected and proven
     */
    private fun addFriend(endpointId: String, paired: Boolean) {
        probation.remove(endpointId)?.cancel()
        myNonces -= endpointId
        val phoneId = phoneIds[endpointId]
        val name = names[endpointId] ?: "Friend"
        _state.update { state ->
            state.copy(
                friends = state.friends.filterNot { it.endpointId == endpointId } +
                        ConnectedFriend(endpointId, name, phoneId = phoneId),
                found = state.found.filterNot { it.endpointId == endpointId },
            )
        }
        if (userSearching) stopSearching()
        // keeps the connection (chat, walkie-talkie...) alive with the app in the background
        NearbyService.start(context)
        startKeepAlive()
        requestLibrary(endpointId)
        sendNowPlaying(endpointId)
        startNowPlayingUpdates()
        party.onFriendConnected(endpointId)
        radar.onFriendConnected(endpointId)
        aloneTimer?.cancel()
        if (carDj.open) send(endpointId, carDj.stateMessage())
        if (phoneId != null) {
            lost -= phoneId
            if (paired) compareCodes -= phoneId
            updateReconnect()
        }
        // who we are; on a new pairing one of the two (the smaller id) makes the shared secret
        val secret = if (paired && phoneId != null && book.myId < phoneId) {
            book.newSecret().also { book.remember(phoneId, friendName(endpointId), it) }
        } else null
        send(endpointId, NearbyMessage(NearbyMessage.HELLO, phoneId = book.myId, secret = secret))
        catchUp(endpointId)
        _state.update { it.copy(rememberedPhones = book.count) }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type == Payload.Type.STREAM) {
                // a friend's live voice (announced by MIC_START just before)
                micStreams.remove(payload.id)?.let { sampleRate ->
                    val walkie = walkieStreams.remove(payload.id)
                    if (walkie != null) listenToTalker(endpointId, payload, sampleRate, walkie)
                    else listenToSinger(endpointId, payload, sampleRate)
                    return
                }
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
        when (message.type) {
            NearbyMessage.HELLO -> return receiveHello(endpointId, message)
            NearbyMessage.PROOF -> return receiveProof(endpointId, message)
        }
        // a bumping phone: only its bumps count, until they match ours
        if (endpointId in bumpCandidates) {
            if (message.type == NearbyMessage.BUMP) onRemoteBump(endpointId)
            return
        }
        // a reconnected phone that hasn't proven who it is yet: nothing else
        if (_state.value.friends.none { it.endpointId == endpointId }) return
        if (message.type == NearbyMessage.UNPAIRED) {
            // they don't recognize this phone any more: stop reconnecting, codes next time
            leaving += endpointId
            phoneIds[endpointId]?.let {
                book.forget(it)
                lost -= it
            }
            _state.update { it.copy(rememberedPhones = book.count) }
            AppMessages.show("${friendName(endpointId)}'s phone forgot this one: connect again with Find friends")
            return
        }
        if (message.type == NearbyMessage.BYE) {
            leaving += endpointId
            phoneIds[endpointId]?.let { lost -= it }
            // left on purpose: off the radar and the map
            radar.forget(phoneIds[endpointId] ?: endpointId)
            return
        }
        if (message.type in Mesh.RELAYED) {
            if (!mesh.isNew(message)) {
                // a message we already have, sent again: its sender missed our receipt, send it again
                if (message.type == NearbyMessage.CHAT) acknowledge(message.origin, message.seq)
                return
            }
            // pass it on to the others (not back to where it came from)
            mesh.forwarded(message)?.let { copy ->
                _state.value.friends.filter { it.endpointId != endpointId }.forEach { send(it.endpointId, copy) }
            }
        }
        if (party.handle(endpointId, message)) return
        if (radar.handle(endpointId, message)) return
        when (message.type) {
            NearbyMessage.LIBRARY_REQUEST -> sendLibrary(endpointId)
            NearbyMessage.LIBRARY_PAGE -> receiveLibraryPage(endpointId, message)
            NearbyMessage.PLAY -> message.songId?.let { playForFriend(endpointId, it) }
            NearbyMessage.COMMAND -> message.command?.let { runCatching { RemoteCommand.valueOf(it) }.getOrNull() }
                ?.let(::runCommand)
            NearbyMessage.STREAM_REQUEST -> message.songId?.let {
                sendSongFile(endpointId, it, askedByFriend = true, purpose = message.purpose, key = message.key)
            }
            NearbyMessage.MIC_START -> message.payloadId?.let {
                micStreams[it] = message.sampleRate ?: LiveMic.SAMPLE_RATE
                if (message.purpose == NearbyMessage.PURPOSE_WALKIE) walkieStreams[it] = message
            }
            NearbyMessage.MIC_STOP -> when (endpointId) {
                // the singer stopped
                singerId -> liveMic.stopListening()
                // the phone we sing through turned us off
                _state.value.singingTo -> {
                    stopSinging(tellThem = false)
                    _state.update { it.copy(error = "${friendName(endpointId)} turned your mic off") }
                }
                else -> Unit
            }
            NearbyMessage.QUEUE_ADD -> message.songId?.let { songId ->
                if (!carDj.open) return@let
                scope.launch {
                    musicRepo.getAllLocalMusicFiles().getOrDefault(emptyList()).find { it.id == songId }
                        ?.let { carDj.add(it, friendName(endpointId)) }
                }
            }
            NearbyMessage.DJ_STATE -> updateFriend(endpointId) {
                it.copy(djOpen = message.djOpen == true, upNext = message.queue.orEmpty())
            }
            NearbyMessage.CHAT -> receiveChat(endpointId, message)
            NearbyMessage.PING -> message.battery?.let { level ->
                updateFriend(endpointId) { it.copy(battery = level, charging = message.charging == true) }
            }
            NearbyMessage.CHAT_ACK -> receiveAck(endpointId, message)
            NearbyMessage.FILE_INFO -> receiveFileInfo(endpointId, message)
            NearbyMessage.SONG_ART -> message.payloadId?.let { id -> message.art?.let { incomingArt[id] = it } }
            NearbyMessage.SONG_LYRICS -> message.payloadId?.let { id ->
                val synced = message.synced
                val plain = message.lyrics ?: synced?.let(::lrcToPlainText)
                if (plain != null) incomingLyrics[id] = LyricsDomain(lyrics = plain, synced = synced)
            }
            NearbyMessage.FILE_FAILED -> if (message.purpose == NearbyMessage.PURPOSE_BLEND && message.key != null) {
                blendPlayer.onSongFailed(message.key)
            } else message.songId?.let { songId ->
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

    /** "this is me": remembers the phone; with a secret, the pairing; with a question, answers it */
    private fun receiveHello(endpointId: String, message: NearbyMessage) {
        // the id it advertised is the one it has to prove; a different one later is ignored
        val theirId = phoneIds.getOrPut(endpointId) { message.phoneId ?: return }
        if (message.phoneId != null && message.phoneId != theirId) return
        val isFriend = _state.value.friends.any { it.endpointId == endpointId }
        if (isFriend) {
            updateFriend(endpointId) { it.copy(phoneId = theirId) }
            // only over a connection whose codes were compared (or proven): it's really them
            val secret = message.secret
            if (secret != null) book.remember(theirId, friendName(endpointId), secret)
            else book.rename(theirId, friendName(endpointId))
            _state.update { it.copy(rememberedPhones = book.count) }
        }
        val nonce = message.nonce ?: return
        // our own question bounced back: never answer it
        if (nonce in myNonces.values) return
        val known = book.known(theirId)
        // "I don't know you (any more)": it then compares codes next time
        send(endpointId, NearbyMessage(NearbyMessage.PROOF, proof = known?.let { book.proof(it.secret, nonce, prover = book.myId, verifier = theirId) }))
    }

    /** a reconnected phone answered: the same friend (a friend again) or not (disconnected) */
    private fun receiveProof(endpointId: String, message: NearbyMessage) {
        val nonce = myNonces[endpointId] ?: return
        val theirId = phoneIds[endpointId] ?: return
        val known = book.known(theirId) ?: return
        if (message.proof == book.proof(known.secret, nonce, prover = theirId, verifier = book.myId)) {
            addFriend(endpointId, paired = false)
            AppMessages.show("${friendName(endpointId)} is back in range")
        } else {
            // it forgot us, has another secret, or isn't who it claims: compare codes next time.
            // (Tell it, so it stops reconnecting to us.) A phone relaying both sides' answers in
            // real time could still pass; that needs being in range of both, and is accepted here.
            compareCodes += theirId
            lost -= theirId
            send(endpointId, NearbyMessage(NearbyMessage.UNPAIRED))
            scope.launch {
                delay(300) // the message goes first
                client.disconnectFromEndpoint(endpointId)
                cleanUpEndpoint(endpointId)
            }
            updateReconnect()
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
            .addOnFailureListener { e -> if (e.statusCode() == ConnectionsStatusCodes.STATUS_ENDPOINT_UNKNOWN) connectionBroke(endpointId) }
    }

    private var keepAliveJob: Job? = null

    /** a small "still here" to every friend now and then: a dead connection is noticed within seconds */
    private fun startKeepAlive() {
        if (keepAliveJob?.isActive == true) return
        keepAliveJob = scope.launch {
            while (_state.value.friends.isNotEmpty()) {
                delay(KEEP_ALIVE_MS)
                // (with the battery: friends see it on the radar, and get a warning when it's low)
                val power = batteryOf(context)
                val ping = NearbyMessage(NearbyMessage.PING, battery = power?.first, charging = power?.second)
                _state.value.friends.forEach { send(it.endpointId, ping) }
                resendUnconfirmed()
            }
        }
    }

    /**
     * Nearby says the friend isn't connected any more, but never told us (e.g. Wi-Fi was turned
     * off under a connection that had moved to Wi-Fi): drop it, so reconnecting starts
     * (over Bluetooth, if that's what's left)
     */
    private fun connectionBroke(endpointId: String) {
        if (_state.value.friends.none { it.endpointId == endpointId }) return
        runCatching { client.disconnectFromEndpoint(endpointId) }
        removeFriend(endpointId)
    }

    // ---------------------- helpers ----------------------

    private fun updateFriend(endpointId: String, change: (ConnectedFriend) -> ConnectedFriend) {
        _state.update { state ->
            state.copy(friends = state.friends.map { if (it.endpointId == endpointId) change(it) else it })
        }
    }

    private fun removeFriend(endpointId: String) {
        if (_state.value.friends.none { it.endpointId == endpointId }) return cleanUpEndpoint(endpointId)
        party.onFriendDisconnected(endpointId)
        // (their last position stays on the radar, getting older)
        // dropped out without saying goodbye: look for them, they reconnect by themselves
        val phoneId = phoneIds[endpointId]
        if (phoneId != null && endpointId !in leaving && book.known(phoneId) != null) {
            lost[phoneId] = System.currentTimeMillis()
            AppMessages.show("${friendName(endpointId)} is out of range. Reconnecting when they're back…")
        }
        // sharing with nobody: give friends a while to come back in range, then stop (battery)
        if (_state.value.radar.sharing && _state.value.friends.all { it.endpointId == endpointId }) {
            aloneTimer?.cancel()
            aloneTimer = scope.launch {
                delay(ALONE_STOP_MS)
                // (not while a friend who dropped out is still being looked for)
                if (_state.value.friends.isEmpty() && _state.value.radar.sharing && lost.isEmpty()) {
                    setLocationSharing(false)
                    AppMessages.show("Stopped sharing your location: no friends connected for a while")
                }
            }
        }
        if (_state.value.singingTo == endpointId) stopSinging(tellThem = false)
        if (singerId == endpointId) liveMic.stopListening()
        if (talkerId == endpointId) liveMic.stopListening()
        cleanUpEndpoint(endpointId)
        _state.update { state ->
            state.copy(
                friends = state.friends.filterNot { it.endpointId == endpointId },
                transfers = state.transfers.filterNot { it.endpointId == endpointId },
            )
        }
        if (_state.value.friends.isEmpty()) {
            nowPlayingJob?.cancel()
            nowPlayingJob = null
            stopTalking()
        }
        updateReconnect()
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

    /** the name other phones see while searching, with this phone's lasting id */
    private fun advertisedName() = FriendBook.encodeName(displayName(), book.myId, bump = bumping)

    private fun formatMeters(meters: Float): String =
        if (meters < 1_000f) "${meters.toInt()} m" else "%.1f km".format(meters / 1_000f)

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

        /** friends who dropped out are looked for this long (longer while sharing location) */
        const val RECONNECT_WINDOW_MS = 15 * 60 * 1000L
        const val RECONNECT_SCAN_MS = 20_000L
        const val RECONNECT_PAUSE_MS = 40_000L
        /** after a drop, search nonstop this long */
        const val FAST_RECONNECT_MS = 2 * 60 * 1000L
        /** the phone with the bigger id waits this long before asking too */
        const val RECONNECT_SECOND_ASK_MS = 6_000L
        /** a reconnected phone has this long to prove who it is */
        const val PROOF_TIMEOUT_MS = 15_000L
        /** the group chat keeps this many messages (this session only) */
        const val MAX_CHAT_MESSAGES = 300
        const val MAX_CHAT_CHARS = 500

        /** two bumps this close (as they arrive) are the same bump */
        const val BUMP_WINDOW_MS = 700L
        /** a friend's connection request this long after we searched still shows the codes */
        const val EXPECT_REQUESTS_MS = 3 * 60 * 1000L
        /** "bump to connect" closes by itself after this long */
        const val BUMP_MODE_MS = 3 * 60 * 1000L

        const val KEY_CHECK_MINUTES = "check_minutes"
        const val CHECK_EVERY_MS = 30_000L
        /** a friend's battery at or below this: a warning */
        const val LOW_BATTERY = 15
        const val KEEP_ALIVE_MS = 10_000L

        /** someone reconnecting gets the chat of the last half hour (at most this many messages) */
        const val CATCH_UP_MS = 30 * 60 * 1000L
        const val MAX_CATCH_UP = 60

        /** photo drop: long side in pixels, JPEG quality, how long photos stay on the phone */
        const val MAX_PHOTO_PX = 1600
        const val PHOTO_QUALITY = 82
        const val PHOTO_KEEP_MS = 2 * 24 * 60 * 60 * 1000L

        /** a walkie-talkie message is at most this long */
        const val MAX_TALK_MS = 60_000L

        /** location sharing with no friend connected stops after this long */
        const val ALONE_STOP_MS = 10 * 60 * 1000L

        /** a sent song's picture: this many pixels at most, and short enough for one message */
        const val ART_SIZE = 300
        const val MAX_ART_CHARS = 28_000

        /** this much of a song is enough to start playing it (the rest streams in) */
        const val READY_BYTES = 64 * 1024L

        /** Nearby's limit for one message is 32 KB */
        const val MAX_MESSAGE_BYTES = 32_000
    }
}
