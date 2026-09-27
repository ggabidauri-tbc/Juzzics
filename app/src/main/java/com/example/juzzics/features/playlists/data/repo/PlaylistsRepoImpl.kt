package com.example.juzzics.features.playlists.data.repo

import com.example.juzzics.features.musics.domain.model.MusicFileDomain
import com.example.juzzics.features.playlists.domain.model.PlaylistDomain
import com.example.juzzics.features.playlists.domain.repo.PlaylistsRepo
import java.util.UUID

class PlaylistsRepoImpl : PlaylistsRepo {

    private val playlists = mutableListOf<PlaylistDomain>()

    override suspend fun getPlaylists(): Result<List<PlaylistDomain>> {
        return Result.success(playlists.toList())
    }

    override suspend fun createPlaylist(name: String?): Result<PlaylistDomain> {
        val count = playlists.size + 1
        val finalName = name?.trim()?.ifEmpty { null } ?: "Playlist #$count"
        val newPlaylist = PlaylistDomain(
            id = UUID.randomUUID().toString(),
            name = finalName,
            songs = emptyList()
        )
        playlists.add(newPlaylist)
        return Result.success(newPlaylist)
    }

    override suspend fun addSongToPlaylist(
        playlistId: String,
        song: MusicFileDomain
    ): Result<PlaylistDomain> {
        val index = playlists.indexOfFirst { it.id == playlistId }
        if (index == -1) {
            return Result.failure(IllegalArgumentException("Playlist not found"))
        }
        val target = playlists[index]
        if (target.songs.any { it.id == song.id }) {
            return Result.success(target)
        }
        val updatedSongs = target.songs + song
        val updatedPlaylist = target.copy(songs = updatedSongs)
        playlists[index] = updatedPlaylist
        return Result.success(updatedPlaylist)
    }

    override suspend fun removeSongFromPlaylist(
        playlistId: String,
        songId: Long
    ): Result<PlaylistDomain> {
        val index = playlists.indexOfFirst { it.id == playlistId }
        if (index == -1) {
            return Result.failure(IllegalArgumentException("Playlist not found"))
        }
        val target = playlists[index]
        val updatedSongs = target.songs.filterNot { it.id == songId }
        val updatedPlaylist = target.copy(songs = updatedSongs)
        playlists[index] = updatedPlaylist
        return Result.success(updatedPlaylist)
    }
}
