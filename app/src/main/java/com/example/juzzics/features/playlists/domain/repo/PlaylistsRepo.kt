package com.example.juzzics.features.playlists.domain.repo

import com.example.juzzics.features.musics.domain.model.MusicFileDomain
import com.example.juzzics.features.playlists.domain.model.PlaylistDomain
import kotlinx.coroutines.flow.Flow

interface PlaylistsRepo {
    /** all playlists, updated on every change */
    fun observePlaylists(): Flow<List<PlaylistDomain>>

    /** a blank [name] gets "Playlist #n" */
    suspend fun createPlaylist(name: String?): Result<PlaylistDomain>
    suspend fun renamePlaylist(playlistId: Long, name: String): Result<Unit>
    suspend fun deletePlaylist(playlistId: Long): Result<Unit>
    suspend fun addSongToPlaylist(playlistId: Long, song: MusicFileDomain): Result<Unit>
    suspend fun removeSongFromPlaylist(playlistId: Long, songId: Long): Result<Unit>
    suspend fun reorderSongs(playlistId: Long, songIds: List<Long>): Result<Unit>

    /** ids of songs with a heart */
    fun observeLikedSongIds(): Flow<Set<Long>>

    /** likes the song, or unlikes it if it's already liked */
    suspend fun toggleLike(song: MusicFileDomain): Result<Unit>
}
