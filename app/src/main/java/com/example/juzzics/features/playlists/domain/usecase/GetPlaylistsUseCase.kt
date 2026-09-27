package com.example.juzzics.features.playlists.domain.usecase

import com.example.juzzics.features.playlists.domain.model.PlaylistDomain
import com.example.juzzics.features.playlists.domain.repo.PlaylistsRepo

class GetPlaylistsUseCase(
    private val playlistsRepo: PlaylistsRepo
) {
    suspend operator fun invoke(): Result<List<PlaylistDomain>> =
        playlistsRepo.getPlaylists()
}
