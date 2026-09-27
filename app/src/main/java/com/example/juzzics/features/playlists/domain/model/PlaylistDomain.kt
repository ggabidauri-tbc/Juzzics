package com.example.juzzics.features.playlists.domain.model

import com.example.juzzics.features.musics.domain.model.MusicFileDomain

/** id of the built-in "Liked songs" playlist (always first, can't be renamed or deleted) */
const val LIKED_SONGS_PLAYLIST_ID = -1L

data class PlaylistDomain(
    val id: Long,
    val name: String,
    /** in playlist order */
    val songs: List<MusicFileDomain> = emptyList()
) {
    val isLikedSongs: Boolean get() = id == LIKED_SONGS_PLAYLIST_ID
}
