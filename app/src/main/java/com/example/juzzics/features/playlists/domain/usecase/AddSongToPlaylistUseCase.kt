package com.example.juzzics.features.playlists.domain.usecase

import com.example.juzzics.features.musics.domain.model.MusicFileDomain
import com.example.juzzics.features.playlists.domain.model.PlaylistDomain
import com.example.juzzics.features.playlists.domain.repo.PlaylistsRepo

class AddSongToPlaylistUseCase(
    private val playlistsRepo: PlaylistsRepo
) {
    suspend operator fun invoke(playlistId: String, song: MusicFileDomain): Result<PlaylistDomain> =
        playlistsRepo.addSongToPlaylist(playlistId, song)
}
