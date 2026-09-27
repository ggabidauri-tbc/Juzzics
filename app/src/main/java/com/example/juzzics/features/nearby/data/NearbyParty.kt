package com.example.juzzics.features.nearby.data

import android.os.SystemClock
import com.example.juzzics.features.musics.domain.model.MusicFileDomain
import com.example.juzzics.features.nearby.domain.PartyRole
import com.example.juzzics.features.nearby.domain.PartyState
import com.example.juzzics.features.player.PlayerController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Party mode: every phone plays the same song at the same moment, like one big speaker.
 *
 * The host plays music as usual. Its songs are sent to the guests ahead (the current one and
 * the next), and every couple of seconds it says "song X is at 1:23.456 at my clock time T".
 * Guests line their clock up with the host's (ping / pong, keeping the fastest round trip),
 * work out where the host is *now* and follow: a big gap is fixed by seeking, a small one by
 * playing a few percent faster or slower for a moment (not audible, no jump).
 */
class NearbyParty(
    private val player: PlayerController,
    private val scope: CoroutineScope,
    private val send: (endpointId: String, message: NearbyMessage) -> Unit,
    /** sends one of this phone's songs to a guest, for the party */
    private val sendSong: (endpointId: String, song: MusicFileDomain, partyKey: Long) -> Unit,
    private val nameOf: (endpointId: String) -> String,
    private val onState: (PartyState) -> Unit,
) {
    private var state = PartyState()
        set(value) {
            field = value
            onState(value)
        }

    // ---------------------- host ----------------------

    private val guests = mutableSetOf<String>()
    /** songs each guest already has (party keys) */
    private val sentTo = mutableMapOf<String, MutableSet<Long>>()
    /** the next song for a guest, sent once the current one arrived (so it doesn't share the bandwidth) */
    private val nextFor = mutableMapOf<String, MusicFileDomain>()
    private var hostJobs = listOf<Job>()

    fun startHosting(friends: List<String>) {
        if (state.role != PartyRole.NONE) return
        state = PartyState(role = PartyRole.HOST)
        friends.forEach(::invite)
        hostJobs = listOf(
            // a new song: send it (and the next one) to everyone, and say so right away
            scope.launch {
                player.state
                    .map { it.currentSong?.id to player.wantsToPlay() }
                    .distinctUntilChanged()
                    .collect {
                        guests.forEach(::sendUpcomingSongs)
                        broadcastSync()
                    }
            },
            // and every couple of seconds, so guests stay in sync (and catch seeks)
            scope.launch {
                while (isActive) {
                    delay(SYNC_EVERY_MS)
                    broadcastSync()
                }
            },
        )
    }

    /** host: a friend connected while the party is on, or was there when it started */
    private fun invite(endpointId: String) {
        guests += endpointId
        send(endpointId, NearbyMessage(NearbyMessage.PARTY_START))
        sendUpcomingSongs(endpointId)
        updateGuestNames()
    }

    fun onFriendConnected(endpointId: String) {
        if (state.role == PartyRole.HOST) invite(endpointId)
    }

    fun onFriendDisconnected(endpointId: String) {
        when (state.role) {
            PartyRole.HOST -> removeGuest(endpointId)
            PartyRole.GUEST -> if (endpointId == hostId) stopFollowing()
            PartyRole.NONE -> Unit
        }
    }

    /** host ends it for everyone; a guest leaves */
    fun end() {
        when (state.role) {
            PartyRole.HOST -> {
                guests.forEach { send(it, NearbyMessage(NearbyMessage.PARTY_END)) }
                guests.clear()
                sentTo.clear()
                nextFor.clear()
                hostJobs.forEach { it.cancel() }
                state = PartyState()
            }
            PartyRole.GUEST -> {
                hostId?.let { send(it, NearbyMessage(NearbyMessage.PARTY_LEAVE)) }
                stopFollowing()
            }
            PartyRole.NONE -> Unit
        }
    }

    private fun removeGuest(endpointId: String) {
        guests -= endpointId
        sentTo -= endpointId
        nextFor -= endpointId
        updateGuestNames()
    }

    private fun updateGuestNames() {
        state = state.copy(guestNames = guests.map(nameOf))
    }

    /** the current song right away; the next one after the guest has the current one */
    private fun sendUpcomingSongs(endpointId: String) {
        val playing = player.state.value
        val current = playing.currentSong
        val next = playing.queue.getOrNull(playing.currentIndex + 1)
        val has = sentTo.getOrPut(endpointId) { mutableSetOf() }
        nextFor.remove(endpointId)
        if (current != null && current.id !in has) {
            has += current.id
            sendSong(endpointId, current, current.id)
            next?.let { nextFor[endpointId] = it }
        } else if (next != null) {
            sendIfMissing(endpointId, next)
        }
    }

    private fun sendIfMissing(endpointId: String, song: MusicFileDomain) {
        val has = sentTo.getOrPut(endpointId) { mutableSetOf() }
        if (song.id in has) return
        has += song.id
        sendSong(endpointId, song, song.id)
    }

    private fun broadcastSync() {
        if (guests.isEmpty()) return
        val playing = player.state.value
        val song = playing.currentSong ?: return
        val message = NearbyMessage(
            NearbyMessage.PARTY_SYNC,
            partyKey = song.id,
            positionMs = player.currentPositionMs(),
            hostTime = SystemClock.elapsedRealtime(),
            // wants to play (while a new song loads, "is playing" is briefly false)
            isPlaying = player.wantsToPlay(),
        )
        guests.forEach { send(it, message) }
    }

    // ---------------------- guest ----------------------

    private var hostId: String? = null
    /** host clock minus this phone's clock, from the fastest ping so far */
    private var clockOffset: Long? = null
    private var bestRoundTrip = Long.MAX_VALUE
    private var pingJob: Job? = null
    private var checkJob: Job? = null
    /** host's songs this phone has, by party key */
    private val songs = mutableMapOf<Long, MusicFileDomain>()
    private var lastSync: NearbyMessage? = null
    private var speed = 1f
    /** when this phone last started a song or jumped: loading takes a moment, don't correct during it */
    private var settlingUntil = 0L
    /** right after a song starts: line up exactly with small jumps, instead of slowly with speed */
    private var lockingUntil = 0L
    /** how long a jump takes on this phone (learned): jumps aim this far ahead */
    private var jumpLatency = INITIAL_JUMP_LATENCY_MS
    /** the last correction was a jump: the next measurement shows how far off it landed */
    private var justJumped = false
    /** song this phone started for the party, and when (it takes a moment until the player reports it) */
    private var startedSongId: Long? = null
    private var startedAt = 0L

    private fun joinParty(endpointId: String) {
        if (state.role == PartyRole.HOST) end() // someone else's party wins
        hostId = endpointId
        clockOffset = null
        bestRoundTrip = Long.MAX_VALUE
        state = PartyState(role = PartyRole.GUEST, hostName = nameOf(endpointId), waitingForSong = true)
        pingJob?.cancel()
        // between the host's messages, check here: both clocks are lined up, so where the host
        // is now can be worked out from its last message
        checkJob?.cancel()
        checkJob = scope.launch {
            while (isActive) {
                delay(CHECK_EVERY_MS)
                lastSync?.let(::catchUp)
            }
        }
        pingJob = scope.launch {
            // a burst at the start for a good first estimate, then now and then (clocks drift a little)
            repeat(PING_BURST) {
                ping()
                delay(150)
            }
            while (isActive) {
                delay(PING_EVERY_MS)
                ping()
            }
        }
    }

    private fun stopFollowing() {
        pingJob?.cancel()
        checkJob?.cancel()
        hostId = null
        lastSync = null
        songs.clear()
        setSpeed(1f)
        state = PartyState()
    }

    private fun ping() {
        hostId?.let { send(it, NearbyMessage(NearbyMessage.PARTY_PING, guestTime = SystemClock.elapsedRealtime())) }
    }

    private fun onPong(message: NearbyMessage) {
        val sentAt = message.guestTime ?: return
        val hostTime = message.hostTime ?: return
        val now = SystemClock.elapsedRealtime()
        val roundTrip = now - sentAt
        // the fastest round trip gives the best estimate (least waiting in between); allow a
        // slightly slower one after a while, in case the clocks drifted
        // (the first answer is always taken: MAX_VALUE + 5 would overflow)
        if (bestRoundTrip == Long.MAX_VALUE || roundTrip <= bestRoundTrip + 5) {
            bestRoundTrip = minOf(bestRoundTrip, roundTrip)
            clockOffset = hostTime - (sentAt + roundTrip / 2)
            lastSync?.let(::catchUp)
        }
    }

    /** the host's song arrived */
    fun onSongReady(partyKey: Long, song: MusicFileDomain) {
        if (state.role != PartyRole.GUEST) return
        songs[partyKey] = song
        hostId?.let { send(it, NearbyMessage(NearbyMessage.PARTY_READY, partyKey = partyKey)) }
        lastSync?.let(::catchUp)
    }

    /** plays / pauses / seeks / nudges the speed to be where the host is */
    private fun catchUp(sync: NearbyMessage) {
        val key = sync.partyKey ?: return
        val offset = clockOffset ?: return // no clock yet: next pong
        val song = songs[key]
        if (song == null) {
            // still on its way: don't keep playing the previous one
            if (!state.waitingForSong) state = state.copy(waitingForSong = true)
            if (player.state.value.isPlaying) player.pause()
            return
        }
        if (state.waitingForSong) state = state.copy(waitingForSong = false)

        val hostNow = SystemClock.elapsedRealtime() + offset
        val hostPlaying = sync.isPlaying == true
        val target = (sync.positionMs ?: 0L) + if (hostPlaying) hostNow - (sync.hostTime ?: hostNow) else 0L

        val current = player.state.value
        val now = SystemClock.elapsedRealtime()
        if (current.currentSong?.id != song.id) {
            // just started it: wait for the player to load it, don't start it again
            if (startedSongId == song.id && now - startedAt < START_TIMEOUT_MS) return
            startedSongId = song.id
            startedAt = now
            // start it where the host is (plus the moment it takes to start)
            player.playQueue(listOf(song), 0, source = "Party with ${state.hostName}")
            jumpTo(target + jumpLatency + START_EXTRA_MS, now)
            if (!hostPlaying) player.pause()
            lockingUntil = now + LOCK_MS
            justJumped = false // starting takes longer than a jump: don't learn from it
            return
        }
        if (now < settlingUntil) return
        if (!hostPlaying) {
            if (current.isPlaying) player.pause()
            if (abs(player.currentPositionMs() - target) > SEEK_ABOVE_MS) player.seekToMs(target)
            setSpeed(1f)
            return
        }
        if (!current.isPlaying) {
            if (player.wantsToPlay()) return // still loading
            jumpTo(target + jumpLatency + START_EXTRA_MS, now)
            player.resume()
            lockingUntil = now + LOCK_MS
            justJumped = false
            return
        }

        val behind = target - player.currentPositionMs()
        if (justJumped) {
            // how far off the last jump landed teaches how long jumps take here
            jumpLatency = (jumpLatency + behind / 2).coerceIn(0L, MAX_JUMP_LATENCY_MS)
            justJumped = false
        }
        val locking = now < lockingUntil
        when {
            abs(behind) > (if (locking) LOCK_JUMP_ABOVE_MS else SEEK_ABOVE_MS) -> {
                setSpeed(1f)
                jumpTo(target + jumpLatency, now)
                justJumped = true
            }
            // small drift later in the song: catch up (or wait) over a couple of seconds, no jump
            abs(behind) > IN_SYNC_MS -> setSpeed(1f + (behind / 2000f).coerceIn(-0.05f, 0.05f))
            else -> setSpeed(1f)
        }
    }

    private fun jumpTo(positionMs: Long, now: Long) {
        player.seekToMs(positionMs)
        settlingUntil = now + SETTLE_MS
    }

    private fun setSpeed(value: Float) {
        if (abs(value - speed) < 0.001f) return
        speed = value
        player.setSpeed(value)
    }

    // ---------------------- messages ----------------------

    /** true if it was a party message */
    fun handle(endpointId: String, message: NearbyMessage): Boolean {
        when (message.type) {
            NearbyMessage.PARTY_START -> joinParty(endpointId)
            NearbyMessage.PARTY_END -> if (endpointId == hostId) stopFollowing()
            NearbyMessage.PARTY_LEAVE -> if (state.role == PartyRole.HOST) removeGuest(endpointId)
            NearbyMessage.PARTY_PING -> if (state.role == PartyRole.HOST) send(
                endpointId,
                NearbyMessage(NearbyMessage.PARTY_PONG, guestTime = message.guestTime, hostTime = SystemClock.elapsedRealtime())
            )
            NearbyMessage.PARTY_PONG -> if (endpointId == hostId) onPong(message)
            NearbyMessage.PARTY_READY -> if (state.role == PartyRole.HOST) nextFor.remove(endpointId)?.let {
                sendIfMissing(endpointId, it)
            }
            NearbyMessage.PARTY_SYNC -> if (endpointId == hostId) {
                lastSync = message
                catchUp(message)
            }
            else -> return false
        }
        return true
    }

    private companion object {
        /** host: "I'm here" this often (guests check themselves in between) */
        const val SYNC_EVERY_MS = 1_000L
        /** guest: how often it checks it's still with the host */
        const val CHECK_EVERY_MS = 200L
        const val PING_BURST = 8
        const val PING_EVERY_MS = 15_000L
        /** mid-song, further apart than this: jump there (smaller gaps: speed) */
        const val SEEK_ABOVE_MS = 400L
        /** the first seconds of a song: jump for anything over this, to line up quickly */
        const val LOCK_MS = 6_000L
        const val LOCK_JUMP_ABOVE_MS = 40L
        /** this close counts as together */
        const val IN_SYNC_MS = 20L
        const val INITIAL_JUMP_LATENCY_MS = 60L
        const val MAX_JUMP_LATENCY_MS = 500L
        /** starting a song takes longer than a jump */
        const val START_EXTRA_MS = 150L
        /** after a jump, let the player settle before measuring again */
        const val SETTLE_MS = 350L
        /** a song that hasn't loaded after this long is started again */
        const val START_TIMEOUT_MS = 4_000L
    }
}
