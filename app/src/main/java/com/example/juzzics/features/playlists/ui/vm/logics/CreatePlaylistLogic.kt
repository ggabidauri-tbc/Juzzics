package com.example.juzzics.features.playlists.ui.vm.logics

import com.example.juzzics.features.musics.domain.model.MusicFileDomain
import com.example.juzzics.features.playlists.ui.vm.PlaylistsVM
import com.example.juzzics.features.playlists.ui.vm.PlaylistsVM.Companion.ADD_TO_PLAYLIST_SONG
import com.example.juzzics.features.playlists.ui.vm.PlaylistsVM.Companion.NEW_PLAYLIST_NAME
import com.example.juzzics.features.playlists.ui.vm.PlaylistsVM.Companion.SHOW_CREATE_DIALOG

fun PlaylistsVM.createPlaylist(name: String? = null) =
    launch(emitErrorMsgAction = true) {
        val nameToUse = name ?: !NEW_PLAYLIST_NAME
        val result = createPlaylistUseCase(nameToUse)
        val songToAdd: MusicFileDomain? = ADD_TO_PLAYLIST_SONG()
        if (result.isSuccess && songToAdd != null) {
            val newPlaylist = result.getOrNull()
            if (newPlaylist != null) {
                addSongToPlaylistUseCase(newPlaylist.id, songToAdd)
            }
        }
        NEW_PLAYLIST_NAME("")
        ADD_TO_PLAYLIST_SONG(null)
        SHOW_CREATE_DIALOG(false)
        getPlaylists()
    }
