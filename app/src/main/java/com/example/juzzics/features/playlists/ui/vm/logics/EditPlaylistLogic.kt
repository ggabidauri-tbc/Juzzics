package com.example.juzzics.features.playlists.ui.vm.logics

import com.example.juzzics.features.playlists.ui.vm.PlaylistsVM
import com.example.juzzics.features.playlists.ui.vm.PlaylistsVM.Companion.SELECTED_PLAYLIST_ID
import com.example.juzzics.features.playlists.ui.vm.PlaylistsVM.Companion.SHOW_DELETE_DIALOG
import com.example.juzzics.features.playlists.ui.vm.PlaylistsVM.Companion.SHOW_RENAME_DIALOG

fun PlaylistsVM.renameSelectedPlaylist(name: String) {
    val playlistId = SELECTED_PLAYLIST_ID() ?: return
    launch(emitLoadingAction = false, emitErrorMsgAction = true) {
        renamePlaylistUseCase(playlistId, name).getOrThrow()
        SHOW_RENAME_DIALOG(false)
    }
}

fun PlaylistsVM.deleteSelectedPlaylist() {
    val playlistId = SELECTED_PLAYLIST_ID() ?: return
    launch(emitLoadingAction = false, emitErrorMsgAction = true) {
        deletePlaylistUseCase(playlistId).getOrThrow()
        SHOW_DELETE_DIALOG(false)
        SELECTED_PLAYLIST_ID(null)
    }
}
