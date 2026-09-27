package com.example.juzzics.features.playlists.ui.vm.logics

import com.example.juzzics.features.playlists.ui.vm.PlaylistsVM
import com.example.juzzics.features.playlists.ui.vm.PlaylistsVM.Companion.PLAYLIST_LIST

fun PlaylistsVM.getPlaylists() =
    launch(emitErrorMsgAction = true) {
        call(getPlaylistsUseCase(), PLAYLIST_LIST)
    }
