package com.example.juzzics.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.juzzics.common.base.viewModel.valueOf
import com.example.juzzics.features.home.HomeScreen
import com.example.juzzics.features.home.ui.vm.HomeVM
import com.example.juzzics.features.lyrics.ui.FetchLyricsScreen
import com.example.juzzics.features.lyrics.ui.vm.FetchLyricsVM
import com.example.juzzics.features.musics.ui.MusicsScreen
import com.example.juzzics.features.musics.ui.model.toDomain
import com.example.juzzics.features.musics.ui.vm.MusicVM
import com.example.juzzics.features.nearby.ui.NearbyScreen
import com.example.juzzics.features.nearby.ui.vm.NearbyVM
import com.example.juzzics.features.player.OpenPlayerRequests
import com.example.juzzics.features.playlists.ui.PlaylistsScreen
import com.example.juzzics.features.playlists.ui.components.AddToPlaylistDialog
import com.example.juzzics.features.playlists.ui.vm.PlaylistsVM
import org.koin.androidx.compose.koinViewModel

@Composable
fun BottomNavigation(navController: NavHostController) {
    val openPlayer = { navController.navigateToTab(Screen.MusicsScreen.route) }

    // notification / widget asked to open the player
    val openPlayerRequests by OpenPlayerRequests.count.collectAsState()
    LaunchedEffect(openPlayerRequests) {
        if (openPlayerRequests > OpenPlayerRequests.handledByNavigation) {
            OpenPlayerRequests.handledByNavigation = openPlayerRequests
            openPlayer()
        }
    }

    NavHost(navController = navController, startDestination = Screen.HomeScreen.route) {
        composable(Screen.HomeScreen.route) {
            val vm: HomeVM = koinViewModel()
            HomeScreen(states = vm.stateList, onAction = vm::onAction, onOpenPlayer = openPlayer)
        }
        composable(Screen.MusicsScreen.route) {
            val musicVm: MusicVM = koinViewModel()
            val playlistsVm: PlaylistsVM = koinViewModel()

            MusicsScreen(
                states = musicVm.stateList,
                uiEvent = musicVm.uiEvent,
                onAction = musicVm::onAction,
                onAddToPlaylist = { musicUi ->
                    playlistsVm.onAction(
                        PlaylistsVM.ShowAddToPlaylistDialogAction(song = musicUi.toDomain(), show = true)
                    )
                }
            )

            val playlistStates = playlistsVm.stateList
            val songToAdd = playlistStates.valueOf(PlaylistsVM.ADD_TO_PLAYLIST_SONG)
            if (playlistStates.valueOf(PlaylistsVM.SHOW_ADD_TO_PLAYLIST) && songToAdd != null) {
                AddToPlaylistDialog(
                    song = songToAdd,
                    playlists = playlistStates.valueOf(PlaylistsVM.PLAYLIST_LIST),
                    onAddToPlaylist = { playlistId ->
                        playlistsVm.onAction(PlaylistsVM.AddSongToPlaylistAction(playlistId, songToAdd))
                    },
                    onCreateAndAdd = { name ->
                        playlistsVm.onAction(PlaylistsVM.CreatePlaylistAction(name))
                    },
                    onDismiss = {
                        playlistsVm.onAction(PlaylistsVM.ShowAddToPlaylistDialogAction(song = null, show = false))
                    }
                )
            }
        }
        composable(Screen.PlaylistsScreen.route) {
            val vm: PlaylistsVM = koinViewModel()
            PlaylistsScreen(
                states = vm.stateList,
                uiEvent = vm.uiEvent,
                onAction = vm::onAction,
                onOpenPlayer = openPlayer
            )
        }
        composable(Screen.FetchLyricsScreen.route) {
            val vm: FetchLyricsVM = koinViewModel()
            FetchLyricsScreen(states = vm.stateList, uiEvent = vm.uiEvent, onAction = vm::onAction)
        }
        composable(Screen.NearbyScreen.route) {
            val vm: NearbyVM = koinViewModel()
            NearbyScreen(states = vm.stateList, onAction = vm::onAction)
        }
    }
}
