package com.example.juzzics.features.playlists.ui.vm

import com.example.juzzics.common.base.viewModel.Action
import com.example.juzzics.common.base.viewModel.BaseViewModel
import com.example.juzzics.common.base.viewModel.State
import com.example.juzzics.features.musics.domain.model.MusicFileDomain
import com.example.juzzics.features.playlists.domain.model.PlaylistDomain
import com.example.juzzics.features.playlists.domain.usecase.AddSongToPlaylistUseCase
import com.example.juzzics.features.playlists.domain.usecase.CreatePlaylistUseCase
import com.example.juzzics.features.playlists.domain.usecase.GetPlaylistsUseCase
import com.example.juzzics.features.playlists.domain.usecase.RemoveSongFromPlaylistUseCase
import com.example.juzzics.features.playlists.ui.vm.logics.addSongToPlaylist
import com.example.juzzics.features.playlists.ui.vm.logics.createPlaylist
import com.example.juzzics.features.playlists.ui.vm.logics.getPlaylists
import com.example.juzzics.features.playlists.ui.vm.logics.removeSongFromPlaylist

class PlaylistsVM(
    val getPlaylistsUseCase: GetPlaylistsUseCase,
    val createPlaylistUseCase: CreatePlaylistUseCase,
    val addSongToPlaylistUseCase: AddSongToPlaylistUseCase,
    val removeSongFromPlaylistUseCase: RemoveSongFromPlaylistUseCase
) : BaseViewModel(
    states = mutableMapOf(
        PLAYLIST_LIST to State<List<PlaylistDomain>>(emptyList()),
        SELECTED_PLAYLIST to State<PlaylistDomain>(null),
        NEW_PLAYLIST_NAME to State(""),
        SHOW_CREATE_DIALOG to State(false),
        ADD_TO_PLAYLIST_SONG to State<MusicFileDomain>(null)
    )
) {
    companion object {
        const val PLAYLIST_LIST = "PlaylistList"
        const val SELECTED_PLAYLIST = "SelectedPlaylist"
        const val NEW_PLAYLIST_NAME = "NewPlaylistName"
        const val SHOW_CREATE_DIALOG = "ShowCreateDialog"
        const val ADD_TO_PLAYLIST_SONG = "AddToPlaylistSong"
    }

    init {
        getPlaylists()
    }

    override fun onAction(action: Action) {
        when (action) {
            is GetPlaylistsAction -> getPlaylists()
            is CreatePlaylistAction -> createPlaylist(action.name)
            is UpdateNewPlaylistNameAction -> NEW_PLAYLIST_NAME(action.name)
            is AddSongToPlaylistAction -> addSongToPlaylist(action.playlistId, action.song)
            is RemoveSongFromPlaylistAction -> removeSongFromPlaylist(action.playlistId, action.songId)
            is SelectPlaylistAction -> SELECTED_PLAYLIST(action.playlist)
            is ToggleCreateDialogAction -> SHOW_CREATE_DIALOG(action.show)
            is ShowAddToPlaylistDialogAction -> {
                ADD_TO_PLAYLIST_SONG(action.song)
                SHOW_CREATE_DIALOG(action.show)
            }
        }
    }

    data object GetPlaylistsAction : Action
    data class CreatePlaylistAction(val name: String? = null) : Action
    data class UpdateNewPlaylistNameAction(val name: String) : Action
    data class AddSongToPlaylistAction(val playlistId: String, val song: MusicFileDomain) : Action
    data class RemoveSongFromPlaylistAction(val playlistId: String, val songId: Long) : Action
    data class SelectPlaylistAction(val playlist: PlaylistDomain?) : Action
    data class ToggleCreateDialogAction(val show: Boolean) : Action
    data class ShowAddToPlaylistDialogAction(val song: MusicFileDomain?, val show: Boolean) : Action
}
