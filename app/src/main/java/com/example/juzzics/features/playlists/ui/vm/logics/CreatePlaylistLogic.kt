package com.example.juzzics.features.playlists.ui.vm.logics

import com.example.juzzics.features.playlists.ui.vm.PlaylistsVM
import com.example.juzzics.features.playlists.ui.vm.PlaylistsVM.Companion.ADD_TO_PLAYLIST_SONG
import com.example.juzzics.features.playlists.ui.vm.PlaylistsVM.Companion.NEW_PLAYLIST_NAME
import com.example.juzzics.features.playlists.ui.vm.PlaylistsVM.Companion.SHOW_ADD_TO_PLAYLIST
import com.example.juzzics.features.playlists.ui.vm.PlaylistsVM.Companion.SHOW_CREATE_DIALOG

/** creates a playlist; if a song is waiting to be added (from Musics), adds it to the new one */
fun PlaylistsVM.createPlaylist(name: String?) =
    launch(emitLoadingAction = false, emitErrorMsgAction = true) {
        val songToAdd = ADD_TO_PLAYLIST_SONG()
        createPlaylistUseCase(name ?: !NEW_PLAYLIST_NAME)
            .onSuccess { playlist ->
                if (songToAdd != null) addSongToPlaylistUseCase(playlist.id, songToAdd)
            }
            .getOrThrow()
        NEW_PLAYLIST_NAME("")
        ADD_TO_PLAYLIST_SONG(null)
        SHOW_CREATE_DIALOG(false)
        SHOW_ADD_TO_PLAYLIST(false)
    }
