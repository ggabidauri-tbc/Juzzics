package com.example.juzzics.features.playlists.domain.repo

import com.example.juzzics.features.musics.domain.model.MusicFileDomain
import com.example.juzzics.features.playlists.domain.model.PlaylistDomain

interface PlaylistsRepo {
    suspend fun getPlaylists(): Result<List<PlaylistDomain>>
    suspend fun createPlaylist(name: String?): Result<PlaylistDomain>
    suspend fun addSongToPlaylist(playlistId: String, song: MusicFileDomain): Result<PlaylistDomain>
    suspend fun removeSongFromPlaylist(playlistId: String, songId: Long): Result<PlaylistDomain>
}
