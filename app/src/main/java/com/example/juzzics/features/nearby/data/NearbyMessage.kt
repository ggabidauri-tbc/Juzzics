package com.example.juzzics.features.nearby.data

import com.example.juzzics.features.nearby.domain.QueueEntry
import com.example.juzzics.features.nearby.domain.RemoteSong

/**
 * What two phones say to each other, sent as small JSON messages.
 * One flat class (instead of one class per message) keeps the JSON simple and robust.
 */
data class NearbyMessage(
    val type: String,
    /** LIBRARY_PAGE */
    val songs: List<RemoteSong>? = null,
    val page: Int? = null,
    val pageCount: Int? = null,
    /** PLAY, STREAM_REQUEST, FILE_INFO, FILE_FAILED */
    val songId: Long? = null,
    /** COMMAND: a [com.example.juzzics.features.nearby.domain.RemoteCommand] name */
    val command: String? = null,
    /** NOW_PLAYING */
    val title: String? = null,
    val artist: String? = null,
    val isPlaying: Boolean? = null,
    /** FILE_INFO: which file payload this describes, and about the song (title / artist above) */
    val payloadId: Long? = null,
    val durationMs: Long? = null,
    /** file extension, e.g. "mp3" */
    val extension: String? = null,
    /** FILE_INFO: the file's size (it streams in, so the receiver doesn't know it otherwise) */
    val sizeBytes: Long? = null,
    /** FILE_INFO: the receiver may save it to their music */
    val canSave: Boolean? = null,
    /** FILE_INFO: [PURPOSE_PARTY] = keep it for party mode instead of playing it right away */
    val purpose: String? = null,
    /** SONG_ART: JPEG, base64 */
    val art: String? = null,
    /** SONG_LYRICS: plain text and LRC */
    val lyrics: String? = null,
    val synced: String? = null,
    /** party: which of the host's songs (the host's song id) */
    val partyKey: Long? = null,
    /** PARTY_SYNC: where the host's song is, at [hostTime] */
    val positionMs: Long? = null,
    /** PARTY_SYNC / PARTY_PONG: the host's clock (elapsedRealtime) */
    val hostTime: Long? = null,
    /** PARTY_PING / PARTY_PONG: the guest's clock when it asked */
    val guestTime: Long? = null,
    /** STREAM_REQUEST / FILE_INFO / FILE_FAILED: what the asker uses to match the answer (blend) */
    val key: Long? = null,
    /** DJ_STATE: songs can be added to the sender's queue, and what's in it */
    val djOpen: Boolean? = null,
    val queue: List<QueueEntry>? = null,
    /** MIC_START: the live voice's sample rate */
    val sampleRate: Int? = null,
    /** LOCATION: GPS position and how exact it is (meters) */
    val lat: Double? = null,
    val lon: Double? = null,
    val accuracy: Float? = null,
    /** HELLO: the sender's lasting phone id; a fresh pairing secret; a challenge to answer with PROOF */
    val phoneId: String? = null,
    val secret: String? = null,
    val nonce: String? = null,
    /** PROOF: HMAC of the challenge with the pair's secret */
    val proof: String? = null,
    /** relayed messages: who said it first (phone id and name), their counter, how many phones it passed */
    val origin: String? = null,
    val originName: String? = null,
    val seq: Long? = null,
    val hops: Int? = null,
    /** CHAT: the message */
    val text: String? = null,
) {
    companion object {
        /** "send me your songs" */
        const val LIBRARY_REQUEST = "library_request"
        /** part of the song list (a message can only be ~32 KB) */
        const val LIBRARY_PAGE = "library_page"
        /** "play this song of yours" */
        const val PLAY = "play"
        const val COMMAND = "command"
        const val NOW_PLAYING = "now_playing"
        /** "send me this song, I want to hear it on my phone" ([purpose] / [key] come back in FILE_INFO) */
        const val STREAM_REQUEST = "stream_request"
        /** "the file coming next is this song, play it" */
        const val FILE_INFO = "file_info"
        /** "couldn't send you that song" */
        const val FILE_FAILED = "file_failed"
        /** the picture of the song whose file is coming */
        const val SONG_ART = "song_art"
        /** the lyrics of the song whose file is coming */
        const val SONG_LYRICS = "song_lyrics"

        /** host: "join my party"; host: "the party is over"; guest: "I'm leaving" */
        const val PARTY_START = "party_start"
        const val PARTY_END = "party_end"
        const val PARTY_LEAVE = "party_leave"
        /** host: "I'm at [positionMs] of song [partyKey] (playing or not)" */
        const val PARTY_SYNC = "party_sync"
        /** guest asks the host's clock, to line both clocks up */
        const val PARTY_PING = "party_ping"
        /** guest: "song [partyKey] arrived" (the host then sends the next one) */
        const val PARTY_READY = "party_ready"
        const val PARTY_PONG = "party_pong"

        const val PURPOSE_PARTY = "party"
        /** a song for the Car DJ's queue */
        const val PURPOSE_QUEUE = "queue"
        /** a song of a blend (the asker plays it in the mix) */
        const val PURPOSE_BLEND = "blend"
        /** a voice message, played over the music */
        const val PURPOSE_SHOUTOUT = "shoutout"

        /** sing-along: "the stream [payloadId] coming now is my live voice, play it over your music" */
        const val MIC_START = "mic_start"
        /** sing-along: singer "I stopped" / listener "I turned your mic off" */
        const val MIC_STOP = "mic_stop"

        /** friend radar: "I'm here" / "I stopped sharing where I am" */
        const val LOCATION = "location"
        const val LOCATION_OFF = "location_off"

        /** "this is me (lasting id)"; with a nonce: "prove you're who you were" */
        const val HELLO = "hello"
        /** the answer to HELLO's challenge */
        const val PROOF = "proof"
        /** "I'm disconnecting on purpose, don't reconnect" */
        const val BYE = "bye"
        /** "I don't recognize you (any more)": forget the pairing, compare codes next time */
        const val UNPAIRED = "unpaired"

        /** meeting point: "meet here" ([lat] / [lon]) / "never mind" */
        const val PIN = "pin"
        const val PIN_CLEAR = "pin_clear"
        /** "come to me, I'm here" ([lat] / [lon]) */
        const val COME_TO_ME = "come_to_me"

        /** group chat: [text], maybe with where the sender is ([lat] / [lon]) */
        const val CHAT = "chat"

        /** MIC_START: a walkie-talkie message (to everyone, played over the music, music ducked) */
        const val PURPOSE_WALKIE = "walkie"

        /** Car DJ: "you can (not) add songs to my queue, here's what's in it" */
        const val DJ_STATE = "dj_state"
        /** Car DJ: "add this song of yours ([songId]) to your queue" */
        const val QUEUE_ADD = "queue_add"
    }
}
