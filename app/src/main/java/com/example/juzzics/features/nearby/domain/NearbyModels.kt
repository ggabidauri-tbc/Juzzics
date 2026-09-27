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
)

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

/** Remote-control commands sent to a friend's phone. */
enum class RemoteCommand { TOGGLE, NEXT, PREVIOUS, VOLUME_UP, VOLUME_DOWN }
