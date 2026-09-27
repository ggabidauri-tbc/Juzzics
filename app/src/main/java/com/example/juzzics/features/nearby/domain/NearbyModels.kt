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
    val error: String? = null,
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
