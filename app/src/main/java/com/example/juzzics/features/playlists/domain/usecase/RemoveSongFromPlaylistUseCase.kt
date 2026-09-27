package com.example.juzzics.features.playlists.domain.usecase

import com.example.juzzics.features.playlists.domain.model.PlaylistDomain
import com.example.juzzics.features.playlists.domain.repo.PlaylistsRepo

class RemoveSongFromPlaylistUseCase(
    private val playlistsRepo: PlaylistsRepo
) {
    suspend operator fun invoke(playlistId: String, songId: Long): Result<PlaylistDomain> =
        playlistsRepo.removeSongFromPlaylist(playlistId, songId)
}
