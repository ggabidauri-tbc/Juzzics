package com.example.juzzics.features.playlists.domain.usecase

import com.example.juzzics.features.playlists.domain.model.PlaylistDomain
import com.example.juzzics.features.playlists.domain.repo.PlaylistsRepo

class CreatePlaylistUseCase(
    private val playlistsRepo: PlaylistsRepo
) {
    suspend operator fun invoke(name: String?): Result<PlaylistDomain> =
        playlistsRepo.createPlaylist(name)
}
