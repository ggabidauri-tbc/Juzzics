package com.example.juzzics.features.playlists.domain.usecase

import com.example.juzzics.features.musics.domain.model.MusicFileDomain
import com.example.juzzics.features.playlists.domain.model.PlaylistDomain
import com.example.juzzics.features.playlists.domain.repo.PlaylistsRepo
import kotlinx.coroutines.flow.Flow

class GetPlaylistsUseCase(private val repo: PlaylistsRepo) {
    operator fun invoke(): Flow<List<PlaylistDomain>> = repo.observePlaylists()
}

class CreatePlaylistUseCase(private val repo: PlaylistsRepo) {
    suspend operator fun invoke(name: String?): Result<PlaylistDomain> = repo.createPlaylist(name)
}

class RenamePlaylistUseCase(private val repo: PlaylistsRepo) {
    suspend operator fun invoke(playlistId: Long, name: String) = repo.renamePlaylist(playlistId, name)
}

class DeletePlaylistUseCase(private val repo: PlaylistsRepo) {
    suspend operator fun invoke(playlistId: Long) = repo.deletePlaylist(playlistId)
}

class AddSongToPlaylistUseCase(private val repo: PlaylistsRepo) {
    suspend operator fun invoke(playlistId: Long, song: MusicFileDomain) =
        repo.addSongToPlaylist(playlistId, song)
}

class RemoveSongFromPlaylistUseCase(private val repo: PlaylistsRepo) {
    suspend operator fun invoke(playlistId: Long, songId: Long) =
        repo.removeSongFromPlaylist(playlistId, songId)
}

class ReorderPlaylistSongsUseCase(private val repo: PlaylistsRepo) {
    suspend operator fun invoke(playlistId: Long, songIds: List<Long>) =
        repo.reorderSongs(playlistId, songIds)
}

class ObserveLikedSongIdsUseCase(private val repo: PlaylistsRepo) {
    operator fun invoke(): Flow<Set<Long>> = repo.observeLikedSongIds()
}

class ToggleLikeUseCase(private val repo: PlaylistsRepo) {
    suspend operator fun invoke(song: MusicFileDomain) = repo.toggleLike(song)
}
