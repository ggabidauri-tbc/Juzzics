package com.example.juzzics.features.nearby.data

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
        /** "send me this song, I want to hear it on my phone" */
        const val STREAM_REQUEST = "stream_request"
        /** "the file coming next is this song, play it" */
        const val FILE_INFO = "file_info"
        /** "couldn't send you that song" */
        const val FILE_FAILED = "file_failed"
    }
}
