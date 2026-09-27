package com.example.juzzics.features.nearby.domain

/** A phone found nearby that runs Juzzics with "Share my music" on. */
data class NearbyDevice(val endpointId: String, val name: String)

/**
 * A connection waiting to be accepted. Both phones show the same [code]: if they match,
 * you're connecting to the right person.
 */
data class PendingConnection(
    val endpointId: String,
    val name: String,
    val code: String,
    /** true: they asked to connect to us; false: we asked them */
    val incoming: Boolean,
    /** we tapped "Codes match" and are waiting for the other phone to do the same */
    val accepted: Boolean = false,
)

/** A song on a friend's phone. */
data class RemoteSong(
    val id: Long,
    val title: String,
    val artist: String,
    val durationMs: Long,
)

/** What a friend's phone is playing. */
data class RemoteNowPlaying(val title: String, val artist: String, val isPlaying: Boolean)

/** A connected friend. */
data class ConnectedFriend(
    val endpointId: String,
    val name: String,
    /** their songs (filled in as they arrive, big libraries come in pages) */
    val library: List<RemoteSong> = emptyList(),
    val libraryComplete: Boolean = false,
    val nowPlaying: RemoteNowPlaying? = null,
    /** their phone is the Car DJ: songs can be added to its queue */
    val djOpen: Boolean = false,
    /** their "Up next" (when [djOpen]) */
    val upNext: List<QueueEntry> = emptyList(),
    /** their phone's lasting id (the endpoint id changes with every connection); null until it says hello */
    val phoneId: String? = null,
    /** their phone's battery (0..100), null until known */
    val battery: Int? = null,
    val charging: Boolean = false,
)

/** A song in a Car DJ queue, and who added it (blank: the phone's owner). */
data class QueueEntry(val title: String, val artist: String, val addedBy: String)

/** Everything the Nearby screen shows. */
data class NearbyState(
    val deviceName: String = "",
    /** others can find this phone and play its music */
    val sharing: Boolean = false,
    /** looking for friends' phones */
    val searching: Boolean = false,
    val found: List<NearbyDevice> = emptyList(),
    val pending: PendingConnection? = null,
    val friends: List<ConnectedFriend> = emptyList(),
    /** songs being sent to / from this phone */
    val transfers: List<SongTransfer> = emptyList(),
    /** friends may save songs this phone sends them */
    val letFriendsSave: Boolean = true,
    val party: PartyState = PartyState(),
    /** Car DJ: this phone lets connected friends add songs to its queue */
    val carDj: Boolean = false,
    /** this phone's "Up next" while it's the Car DJ */
    val djQueue: List<QueueEntry> = emptyList(),
    /** holding the shout-out button */
    val recordingShoutOut: Boolean = false,
    /** a friend's shout-out playing right now: their name */
    val shoutOutFrom: String? = null,
    /** this phone's mic streams live to this friend's phone (endpoint id) */
    val singingTo: String? = null,
    /** a friend singing through this phone right now: their name */
    val singer: String? = null,
    /** how loud a singing friend's voice plays here (1 = as recorded) */
    val micGain: Float = 2f,
    /** friend radar: where everyone is */
    val radar: RadarState = RadarState(),
    /** friends who dropped out of range: this phone looks for them and reconnects by itself */
    val reconnecting: List<String> = emptyList(),
    /** phones paired before (they reconnect without comparing codes) */
    val rememberedPhones: Int = 0,
    /** walkie-talkie: this phone's mic is live to everyone connected */
    val talking: Boolean = false,
    /** walkie-talkie: a friend talking right now: their name */
    val talker: String? = null,
    /** the group chat (this session only), oldest first */
    val chat: List<ChatMessage> = emptyList(),
    /** chat messages that came while the chat wasn't open */
    val unreadChat: Int = 0,
    /** "check on friends": alert when someone sharing hasn't moved / hasn't been heard of this long (0 = off) */
    val checkMinutes: Int = 30,
    val error: String? = null,
)

/** A message in the group chat. [lat] / [lon]: where the sender was, if they shared it. */
data class ChatMessage(
    val id: String,
    val from: String,
    val fromMe: Boolean,
    val text: String,
    /** System.currentTimeMillis when it arrived / was sent */
    val atMs: Long,
    /** the sender's phone id (to find them on the radar) */
    val personId: String? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    /** came through other phones */
    val relayed: Boolean = false,
    /** photo drop: the photo (a file on this phone), [text] is its caption */
    val photoPath: String? = null,
    /** mine, written while nobody was connected: goes out when someone is back */
    val pending: Boolean = false,
    /** mine: who it arrived at (their phone id to name), from their receipts */
    val deliveredTo: Map<String, String> = emptyMap(),
)

/** A song file on its way between two phones. */
data class SongTransfer(
    val key: String,
    val endpointId: String,
    val title: String,
    val friendName: String,
    /** true: coming to this phone; false: this phone is sending it */
    val incoming: Boolean,
    /** 0..1, null while waiting for the other phone to start sending */
    val progress: Float? = null,
)

enum class PartyRole { NONE, HOST, GUEST }

/** Party mode: all phones play the same song at the same moment, following the host phone. */
data class PartyState(
    val role: PartyRole = PartyRole.NONE,
    /** GUEST: whose party */
    val hostName: String = "",
    /** HOST: phones following this one */
    val guestNames: List<String> = emptyList(),
    /** GUEST: the host's current song is still on its way to this phone */
    val waitingForSong: Boolean = false,
)

/** Remote-control commands sent to a friend's phone. */
enum class RemoteCommand { TOGGLE, NEXT, PREVIOUS, VOLUME_UP, VOLUME_DOWN }

/** A song in a blend: whose it is ([ownerId] null = this phone's). */
data class BlendItem(val ownerId: String?, val ownerName: String, val song: RemoteSong)

/** One of this phone's songs that friends have too. */
data class SharedSong(val song: RemoteSong, val alsoWith: List<String>)

/** Your music mixed with your friends': [mix] takes turns between everyone, [shared] = what you have in common. */
data class Blend(
    val people: List<String>,
    val mix: List<BlendItem>,
    val shared: List<SharedSong>,
    /** how your taste compares with each friend's */
    val matches: List<TasteMatch> = emptyList(),
)

/**
 * "You and Anna: 67% match". [percent] mixes shared songs and shared artists (relative to
 * the smaller library, so a small library can still be a twin of a big one).
 */
data class TasteMatch(
    val friendName: String,
    val percent: Int,
    val sharedSongs: Int,
    /** artists you both have, most songs first */
    val sharedArtists: List<String>,
    /** your most-collected artist they don't have, and theirs you don't */
    val youBring: String?,
    val theyBring: String?,
) {
    val label: String
        get() = when {
            percent >= 80 -> "Music twins"
            percent >= 60 -> "Great match"
            percent >= 40 -> "Good match"
            percent >= 20 -> "Some common ground"
            else -> "Opposites attract"
        }
}

/** Where a song picked from a friend's list plays. */
enum class PlayTarget {
    /** on their phone, right now */
    THEIR_PHONE,
    /** added to their queue (their phone is the Car DJ) */
    THEIR_QUEUE,
    /** sent over and played on this phone */
    MY_PHONE,
}

/** The "Together" activities, each opened as its own page. */
enum class NearbyPanel(val title: String) {
    PARTY("Party"),
    CAR_DJ("Car DJ"),
    SING("Sing along"),
    SHOUT_OUT("Shout-out"),
    RECEIVED("Songs friends sent"),
    RADAR("Friend radar"),
    CHAT("Group chat"),
}

/** A point on a trail. */
data class GeoPoint(val lat: Double, val lon: Double)

/** A GPS position, and when this phone got it (elapsedRealtime). */
data class GeoFix(val lat: Double, val lon: Double, val accuracyM: Float, val atElapsedMs: Long)

/** Someone on the radar: a connected friend, or someone further away whose position came through friends' phones. */
data class RadarPerson(
    val name: String,
    val fix: GeoFix,
    /** came through other phones (not connected to this one directly) */
    val relayed: Boolean,
    /** their phone's battery (0..100), null if unknown (an older Juzzics) */
    val battery: Int? = null,
    val charging: Boolean = false,
    /** since when they haven't really moved (elapsedRealtime), for "hasn't moved for 30 min" */
    val stillSinceMs: Long? = null,
)

/** A meeting point someone set ("meet here"). */
data class MeetingPin(val setBy: String, val lat: Double, val lon: Double, val mine: Boolean)

/** A friend asked you to come to them. */
data class ComeToMe(val from: String, val personId: String, val lat: Double, val lon: Double, val atElapsedMs: Long)

/** Friend radar: this phone's position, everyone else's, and whether this phone shares its own. */
data class RadarState(
    /** friends see this phone's position */
    val sharing: Boolean = false,
    /** sharing stops by itself at this time (System.currentTimeMillis); null = until turned off */
    val sharingUntilMs: Long? = null,
    val me: GeoFix? = null,
    /**
     * everyone sharing where they are, by person id (their phone's lasting id). Kept after they
     * drop out of range: their last known position, getting older
     */
    val people: Map<String, RadarPerson> = emptyMap(),
    /** the phone's location is turned off (quick settings) */
    val locationOff: Boolean = false,
    /** the compass says it's unsure (needs a figure-8 wave to calibrate) */
    val compassUnreliable: Boolean = false,
    /** where everyone walked ([ME] = this phone, else the person id), oldest first */
    val trails: Map<String, List<GeoPoint>> = emptyMap(),
    /** meeting points, by who set them ([ME] = this phone) */
    val pins: Map<String, MeetingPin> = emptyMap(),
    /** others' meeting points hidden on this phone (shown again with "Show hidden", or when moved) */
    val hiddenPins: Set<String> = emptySet(),
    /** the latest "come to me" (until dismissed) */
    val comeToMe: ComeToMe? = null,
) {
    companion object {
        /** [trails] / [pins] key of this phone */
        const val ME = "me"
    }
}

