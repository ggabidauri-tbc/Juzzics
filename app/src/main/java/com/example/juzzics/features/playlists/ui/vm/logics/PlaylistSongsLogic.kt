package com.example.juzzics.features.playlists.ui.vm.logics

import com.example.juzzics.common.messages.AppMessages
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
        val name = PLAYLIST_LIST().find { it.id == playlistId }?.name ?: "the playlist"
        AppMessages.show("Added to $name")
    }

/** removes it right away; Undo puts it back where it was */
fun PlaylistsVM.removeSongFromPlaylist(playlistId: Long, songId: Long) {
    val playlist = PLAYLIST_LIST().find { it.id == playlistId } ?: return
    val song = playlist.songs.find { it.id == songId } ?: return
    val order = playlist.songs.map { it.id }
    launch(emitLoadingAction = false, emitErrorMsgAction = true) {
        removeSongFromPlaylistUseCase(playlistId, songId).getOrThrow()
        AppMessages.showWithUndo("Removed from ${playlist.name}") {
            launch(emitLoadingAction = false) {
                addSongToPlaylistUseCase(playlistId, song).getOrThrow()
                reorderPlaylistSongsUseCase(playlistId, order).getOrThrow()
            }
        }
    }
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
