package com.example.juzzics.features.playlists.ui.vm.logics

import com.example.juzzics.features.musics.domain.model.MusicFileDomain
import com.example.juzzics.features.playlists.ui.vm.PlaylistsVM
import com.example.juzzics.features.playlists.ui.vm.PlaylistsVM.Companion.ADD_TO_PLAYLIST_SONG
import com.example.juzzics.features.playlists.ui.vm.PlaylistsVM.Companion.SHOW_CREATE_DIALOG

fun PlaylistsVM.addSongToPlaylist(playlistId: String, song: MusicFileDomain) =
    launch(emitErrorMsgAction = true) {
        addSongToPlaylistUseCase(playlistId, song)
        ADD_TO_PLAYLIST_SONG(null)
        SHOW_CREATE_DIALOG(false)
        getPlaylists()
    }
