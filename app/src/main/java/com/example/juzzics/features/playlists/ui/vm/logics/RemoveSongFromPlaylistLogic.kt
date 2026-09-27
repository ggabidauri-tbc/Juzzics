package com.example.juzzics.features.playlists.ui.vm.logics

import com.example.juzzics.features.playlists.ui.vm.PlaylistsVM
import com.example.juzzics.features.playlists.ui.vm.PlaylistsVM.Companion.SELECTED_PLAYLIST

fun PlaylistsVM.removeSongFromPlaylist(playlistId: String, songId: Long) =
    launch(emitErrorMsgAction = true) {
        val result = removeSongFromPlaylistUseCase(playlistId, songId)
        if (result.isSuccess) {
            val updatedPlaylist = result.getOrNull()
            SELECTED_PLAYLIST(updatedPlaylist)
        }
        getPlaylists()
    }
