package com.example.juzzics.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.juzzics.features.home.HomeScreen
import com.example.juzzics.features.home.ui.vm.HomeVM
import com.example.juzzics.features.musics.ui.LibraryScreen
import com.example.juzzics.features.musics.ui.model.MusicFileUi
import com.example.juzzics.features.musics.ui.vm.MusicVM
import com.example.juzzics.features.nearby.ui.NearbyScreen
import com.example.juzzics.features.nearby.ui.vm.NearbyVM
import com.example.juzzics.features.playlists.ui.vm.PlaylistsVM
import org.koin.androidx.compose.koinViewModel

/**
 * The three tabs. The player isn't a tab: it's drawn over all of them (see MainActivity).
 * [playlistsVm] is the app's one playlists ViewModel (also behind "Add to playlist").
 */
@Composable
fun BottomNavigation(
    navController: NavHostController,
    playlistsVm: PlaylistsVM,
    onAddToPlaylist: (MusicFileUi) -> Unit,
) {
    NavHost(navController = navController, startDestination = Screen.HomeScreen.route) {
        composable(Screen.HomeScreen.route) {
            val vm: HomeVM = koinViewModel()
            HomeScreen(
                states = vm.stateList,
                onAction = vm::onAction,
                onOpenLibrary = { navController.navigateToTab(Screen.LibraryScreen.route) },
                onOpenNearby = { navController.navigateToTab(Screen.NearbyScreen.route) },
            )
        }
        composable(Screen.LibraryScreen.route) {
            val vm: MusicVM = koinViewModel()
            LibraryScreen(
                states = vm.stateList,
                uiEvent = vm.uiEvent,
                onAction = vm::onAction,
                playlistStates = playlistsVm.stateList,
                onPlaylistAction = playlistsVm::onAction,
                onAddToPlaylist = onAddToPlaylist,
            )
        }
        composable(Screen.NearbyScreen.route) {
            val vm: NearbyVM = koinViewModel()
            NearbyScreen(states = vm.stateList, onAction = vm::onAction)
        }
    }
}
