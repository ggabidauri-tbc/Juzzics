package com.example.juzzics.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.juzzics.common.base.viewModel.getStateValue
import com.example.juzzics.features.home.HomeScreen
import com.example.juzzics.features.lyrics.ui.FetchLyricsScreen
import com.example.juzzics.features.lyrics.ui.vm.FetchLyricsVM
import com.example.juzzics.features.musics.domain.model.MusicFileDomain
import com.example.juzzics.features.musics.ui.MusicsScreen
import com.example.juzzics.features.musics.ui.model.toDomain
import com.example.juzzics.features.musics.ui.vm.MusicVM
import com.example.juzzics.features.playlists.domain.model.PlaylistDomain
import com.example.juzzics.features.playlists.ui.PlaylistsScreen
import com.example.juzzics.features.playlists.ui.components.AddToPlaylistDialog
import com.example.juzzics.features.playlists.ui.vm.PlaylistsVM
import org.koin.androidx.compose.koinViewModel

@Composable
fun BottomNavigation(navController: NavHostController) {
    NavHost(navController = navController, startDestination = Screen.HomeScreen.route) {
        composable(Screen.HomeScreen.route) {
            HomeScreen()
        }
        composable(Screen.MusicsScreen.route) {
            val musicVm: MusicVM = koinViewModel()
            val playlistsVm: PlaylistsVM = koinViewModel()

            val playlistStates = playlistsVm.stateList
            val playlists = playlistStates
                .getStateValue<List<PlaylistDomain>>(PlaylistsVM.PLAYLIST_LIST).orEmpty()
            val songToAddToPlaylist = playlistStates
                .getStateValue<MusicFileDomain>(PlaylistsVM.ADD_TO_PLAYLIST_SONG)
            val showAddToPlaylistDialog = playlistStates
                .getStateValue<Boolean>(PlaylistsVM.SHOW_CREATE_DIALOG) ?: false

            MusicsScreen(
                states = musicVm.stateList,
                uiEvent = musicVm.uiEvent,
                onAction = musicVm::onAction,
                onAddToPlaylist = { musicUi ->
                    playlistsVm.onAction(
                        PlaylistsVM.ShowAddToPlaylistDialogAction(
                            song = musicUi.toDomain(),
                            show = true
                        )
                    )
                }
            )

            if (showAddToPlaylistDialog && songToAddToPlaylist != null) {
                AddToPlaylistDialog(
                    song = songToAddToPlaylist,
                    playlists = playlists,
                    onAddToPlaylist = { playlistId ->
                        playlistsVm.onAction(
                            PlaylistsVM.AddSongToPlaylistAction(
                                playlistId = playlistId,
                                song = songToAddToPlaylist
                            )
                        )
                    },
                    onCreateAndAdd = { name ->
                        playlistsVm.onAction(
                            PlaylistsVM.CreatePlaylistAction(name)
                        )
                    },
                    onDismiss = {
                        playlistsVm.onAction(
                            PlaylistsVM.ShowAddToPlaylistDialogAction(
                                song = null,
                                show = false
                            )
                        )
                    }
                )
            }
        }
        composable(Screen.PlaylistsScreen.route) {
            val vm: PlaylistsVM = koinViewModel()
            PlaylistsScreen(states = vm.stateList, uiEvent = vm.uiEvent, onAction = vm::onAction)
        }
        composable(Screen.FetchLyricsScreen.route) {
            val vm: FetchLyricsVM = koinViewModel()
            FetchLyricsScreen(states = vm.stateList, uiEvent = vm.uiEvent, onAction = vm::onAction)
        }
    }
}
