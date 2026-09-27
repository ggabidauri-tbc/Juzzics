package com.example.juzzics.features.playlists.ui.vm.logics

import com.example.juzzics.features.musics.domain.model.MusicFileDomain
import com.example.juzzics.features.playlists.ui.vm.PlaylistsVM
import com.example.juzzics.features.playlists.ui.vm.PlaylistsVM.Companion.ADD_TO_PLAYLIST_SONG
import com.example.juzzics.features.playlists.ui.vm.PlaylistsVM.Companion.PLAYLIST_LIST
import com.example.juzzics.features.playlists.ui.vm.PlaylistsVM.Companion.SHOW_ADD_TO_PLAYLIST

fun PlaylistsVM.addSongToPlaylist(playlistId: Long, song: MusicFileDomain) =
    launch(emitLoadingAction = false, emitErrorMsgAction = true) {
        addSongToPlaylistUseCase(playlistId, song).getOrThrow()
        ADD_TO_PLAYLIST_SONG(null)
        SHOW_ADD_TO_PLAYLIST(false)
    }

fun PlaylistsVM.removeSongFromPlaylist(playlistId: Long, songId: Long) =
    launch(emitLoadingAction = false, emitErrorMsgAction = true) {
        removeSongFromPlaylistUseCase(playlistId, songId).getOrThrow()
    }

fun PlaylistsVM.reorderPlaylist(playlistId: Long, songIds: List<Long>) =
    launch(emitLoadingAction = false, emitErrorMsgAction = true) {
        reorderPlaylistSongsUseCase(playlistId, songIds).getOrThrow()
    }

/** plays the playlist in its order, from [startIndex] */
fun PlaylistsVM.playPlaylist(playlistId: Long, startIndex: Int) {
    val playlist = PLAYLIST_LIST().find { it.id == playlistId } ?: return
    player.playQueue(playlist.songs, startIndex, source = playlist.name)
}
