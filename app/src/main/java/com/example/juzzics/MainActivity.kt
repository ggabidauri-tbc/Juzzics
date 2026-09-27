package com.example.juzzics

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.juzzics.common.base.viewModel.valueOf
import com.example.juzzics.common.enums.BottomNavItems
import com.example.juzzics.common.messages.AppMessages
import com.example.juzzics.features.musics.ui.model.MusicFileUi
import com.example.juzzics.features.musics.ui.model.toDomain
import com.example.juzzics.features.onboarding.AudioPermissionGate
import com.example.juzzics.features.player.OpenPlayerRequests
import com.example.juzzics.features.player.ui.MiniPlayerSpace
import com.example.juzzics.features.player.ui.PlayerOverlay
import com.example.juzzics.features.player.ui.vm.PlayerVM
import com.example.juzzics.features.playlists.ui.components.AddToPlaylistDialog
import com.example.juzzics.features.playlists.ui.vm.PlaylistsVM
import com.example.juzzics.navigation.BottomNavigation
import com.example.juzzics.navigation.navigateToTab
import com.example.juzzics.ui.theme.JuzzicsTheme
import org.koin.androidx.compose.koinViewModel

class MainActivity : ComponentActivity() {

    companion object {
        private const val EXTRA_OPEN_PLAYER = "open_player"

        /** opens the app on the full player (notification, widget) */
        fun openPlayerIntent(context: Context): Intent =
            Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_OPEN_PLAYER, true)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // draw behind the system bars; screens get their padding from the Scaffold below
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // only for a fresh start: after a rotation the request was already handled
        if (savedInstanceState == null) handleOpenPlayer(intent)
        setContent {
            JuzzicsTheme {
                AudioPermissionGate { JuzzicsRoot() }
            }
        }
    }

    /** app already open (singleTop): e.g. the notification was tapped */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleOpenPlayer(intent)
    }

    private fun handleOpenPlayer(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_PLAYER, false) == true) {
            intent.removeExtra(EXTRA_OPEN_PLAYER)
            OpenPlayerRequests.request()
        }
    }
}

/**
 * The app: three tabs, the player over all of them (mini player above the bottom bar, swipe
 * up for the full player), messages at the bottom, and "Add to playlist" from anywhere.
 */
@Composable
private fun JuzzicsRoot() {
    val navController = rememberNavController()
    val playerVm: PlayerVM = koinViewModel()
    val playlistsVm: PlaylistsVM = koinViewModel()
    val snackbarHost = remember { SnackbarHostState() }
    AppMessages.Collect(snackbarHost)

    val hasSong = playerVm.stateList.valueOf(PlayerVM.CURRENT) != null
    // the mini player needs free space at the bottom of every tab
    val playerSpace = if (hasSong) MiniPlayerSpace else 0.dp
    val onAddToPlaylist: (MusicFileUi) -> Unit = { song ->
        playlistsVm.onAction(PlaylistsVM.ShowAddToPlaylistDialogAction(song = song.toDomain(), show = true))
    }
    val density = LocalDensity.current
    // the bottom bar: 80 dp + the system's navigation bar
    val bottomBarHeight = 80.dp + with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = { BottomBar(navController) },
            snackbarHost = { SnackbarHost(snackbarHost, Modifier.padding(bottom = playerSpace)) },
        ) { padding ->
            Box(
                Modifier
                    .padding(padding)
                    .padding(bottom = playerSpace)
            ) {
                BottomNavigation(navController, playlistsVm, onAddToPlaylist)
            }
        }

        PlayerOverlay(
            states = playerVm.stateList,
            onAction = playerVm::onAction,
            bottomInset = bottomBarHeight,
            onAddToPlaylist = onAddToPlaylist,
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
                onCreateAndAdd = { name -> playlistsVm.onAction(PlaylistsVM.CreatePlaylistAction(name)) },
                onDismiss = { playlistsVm.onAction(PlaylistsVM.ShowAddToPlaylistDialogAction(song = null, show = false)) }
            )
        }
    }
}

@Composable
fun BottomBar(rootNavController: NavHostController) {
    val rootNavBackStackEntry by rootNavController.currentBackStackEntryAsState()
    NavigationBar { ShowNavBar(rootNavController = rootNavController, rootNavBackStackEntry) }
}

@Composable
fun RowScope.ShowNavBar(
    rootNavController: NavHostController,
    navBackStackEntry: NavBackStackEntry?
) {
    BottomNavItems.entries.forEach { item ->
        val isSelected = item.route == navBackStackEntry?.destination?.route
        NavigationBarItem(
            selected = isSelected,
            onClick = { rootNavController.navigateToTab(item.route) },
            label = { Text(text = item.title) },
            icon = {
                Icon(
                    imageVector = if (isSelected) item.selectedIcon else item.unSelectedIcon,
                    contentDescription = item.title
                )
            }
        )
    }
}
