package com.example.juzzics.features.playlists.ui.vm

import com.example.juzzics.common.base.viewModel.Action
import com.example.juzzics.common.base.viewModel.BaseViewModel
import com.example.juzzics.common.base.viewModel.StateKey
import com.example.juzzics.features.musics.domain.model.MusicFileDomain
import com.example.juzzics.features.player.PlayerController
import com.example.juzzics.features.playlists.domain.model.PlaylistDomain
import com.example.juzzics.features.playlists.domain.usecase.AddSongToPlaylistUseCase
import com.example.juzzics.features.playlists.domain.usecase.CreatePlaylistUseCase
import com.example.juzzics.features.playlists.domain.usecase.DeletePlaylistUseCase
import com.example.juzzics.features.playlists.domain.usecase.GetPlaylistsUseCase
import com.example.juzzics.features.playlists.domain.usecase.RemoveSongFromPlaylistUseCase
import com.example.juzzics.features.playlists.domain.usecase.RenamePlaylistUseCase
import com.example.juzzics.features.playlists.domain.usecase.ReorderPlaylistSongsUseCase
import com.example.juzzics.features.playlists.ui.vm.logics.addSongToPlaylist
import com.example.juzzics.features.playlists.ui.vm.logics.createPlaylist
import com.example.juzzics.features.playlists.ui.vm.logics.deleteSelectedPlaylist
import com.example.juzzics.features.playlists.ui.vm.logics.playPlaylist
import com.example.juzzics.features.playlists.ui.vm.logics.removeSongFromPlaylist
import com.example.juzzics.features.playlists.ui.vm.logics.renameSelectedPlaylist
import com.example.juzzics.features.playlists.ui.vm.logics.reorderPlaylist

class PlaylistsVM(
    getPlaylistsUseCase: GetPlaylistsUseCase,
    val createPlaylistUseCase: CreatePlaylistUseCase,
    val renamePlaylistUseCase: RenamePlaylistUseCase,
    val deletePlaylistUseCase: DeletePlaylistUseCase,
    val addSongToPlaylistUseCase: AddSongToPlaylistUseCase,
    val removeSongFromPlaylistUseCase: RemoveSongFromPlaylistUseCase,
    val reorderPlaylistSongsUseCase: ReorderPlaylistSongsUseCase,
    val player: PlayerController,
) : BaseViewModel(
    listOf(
        PLAYLIST_LIST, SELECTED_PLAYLIST_ID,
        NEW_PLAYLIST_NAME, SHOW_CREATE_DIALOG,
        ADD_TO_PLAYLIST_SONG, SHOW_ADD_TO_PLAYLIST,
        SHOW_RENAME_DIALOG, SHOW_DELETE_DIALOG,
    )
) {
    companion object {
        /** all playlists, kept up to date from the database */
        val PLAYLIST_LIST = StateKey<List<PlaylistDomain>>("playlists", emptyList())
        /** playlist opened in the detail view */
        val SELECTED_PLAYLIST_ID = StateKey<Long?>("selectedPlaylistId", null)

        val NEW_PLAYLIST_NAME = StateKey("newPlaylistName", "")
        val SHOW_CREATE_DIALOG = StateKey("showCreateDialog", false)

        /** song waiting to be added from the Musics screen's "add to playlist" dialog */
        val ADD_TO_PLAYLIST_SONG = StateKey<MusicFileDomain?>("addToPlaylistSong", null)
        val SHOW_ADD_TO_PLAYLIST = StateKey("showAddToPlaylist", false)

        val SHOW_RENAME_DIALOG = StateKey("showRenameDialog", false)
        val SHOW_DELETE_DIALOG = StateKey("showDeleteDialog", false)
    }

    init {
        getPlaylistsUseCase().collectIn(PLAYLIST_LIST)
    }

    override fun onAction(action: Action) {
        when (action) {
            is CreatePlaylistAction -> createPlaylist(action.name)
            is UpdateNewPlaylistNameAction -> NEW_PLAYLIST_NAME(action.name)
            is ToggleCreateDialogAction -> SHOW_CREATE_DIALOG(action.show)
            is SelectPlaylistAction -> SELECTED_PLAYLIST_ID(action.playlistId)

            is ShowAddToPlaylistDialogAction -> {
                ADD_TO_PLAYLIST_SONG(action.song)
                SHOW_ADD_TO_PLAYLIST(action.show)
            }
            is AddSongToPlaylistAction -> addSongToPlaylist(action.playlistId, action.song)
            is RemoveSongFromPlaylistAction -> removeSongFromPlaylist(action.playlistId, action.songId)
            is ReorderPlaylistAction -> reorderPlaylist(action.playlistId, action.songIds)
            is PlayPlaylistAction -> playPlaylist(action.playlistId, action.startIndex)

            is ShowRenameDialogAction -> SHOW_RENAME_DIALOG(action.show)
            is RenamePlaylistAction -> renameSelectedPlaylist(action.name)
            is ShowDeleteDialogAction -> SHOW_DELETE_DIALOG(action.show)
            is DeletePlaylistAction -> deleteSelectedPlaylist()
        }
    }

    /** [name] null: use the name typed in the create dialog */
    data class CreatePlaylistAction(val name: String? = null) : Action
    data class UpdateNewPlaylistNameAction(val name: String) : Action
    data class ToggleCreateDialogAction(val show: Boolean) : Action
    data class SelectPlaylistAction(val playlistId: Long?) : Action

    data class ShowAddToPlaylistDialogAction(val song: MusicFileDomain?, val show: Boolean) : Action
    data class AddSongToPlaylistAction(val playlistId: Long, val song: MusicFileDomain) : Action
    data class RemoveSongFromPlaylistAction(val playlistId: Long, val songId: Long) : Action
    data class ReorderPlaylistAction(val playlistId: Long, val songIds: List<Long>) : Action
    data class PlayPlaylistAction(val playlistId: Long, val startIndex: Int = 0) : Action

    data class ShowRenameDialogAction(val show: Boolean) : Action
    data class RenamePlaylistAction(val name: String) : Action
    data class ShowDeleteDialogAction(val show: Boolean) : Action
    data object DeletePlaylistAction : Action
}
