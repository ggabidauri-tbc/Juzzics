package com.example.juzzics

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.juzzics.common.enums.BottomNavItems
import com.example.juzzics.features.onboarding.AudioPermissionGate
import com.example.juzzics.features.player.OpenPlayerRequests
import com.example.juzzics.navigation.BottomNavigation
import com.example.juzzics.navigation.navigateToTab
import com.example.juzzics.ui.theme.JuzzicsTheme


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
                AudioPermissionGate {
                    val rootNavController = rememberNavController()
                    Scaffold(bottomBar = { BottomBar(rootNavController = rootNavController) }) { paddingValues ->
                        // top (status bar) + bottom (navigation bar) insets
                        Box(modifier = Modifier.padding(paddingValues)) {
                            BottomNavigation(rootNavController)
                        }
                    }
                }
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


@Composable
fun BottomBar(rootNavController: NavHostController) {
    val rootNavBackStackEntry by rootNavController.currentBackStackEntryAsState()
    NavigationBar() { ShowNavBar(rootNavController = rootNavController, rootNavBackStackEntry) }
}

@Composable
fun RowScope.ShowNavBar(
    rootNavController: NavHostController,
    navBackStackEntry: NavBackStackEntry?
) {
    BottomNavItems.entries.forEach { item ->
        val isSelected = item.route == navBackStackEntry?.destination?.route
        NavigationBarItem(selected = isSelected,
            onClick = { rootNavController.navigateToTab(item.route) },
            label = { Text(text = item.title) },
            icon = {
                Icon(
                    imageVector = if (isSelected) item.selectedIcon else item.unSelectedIcon,
                    contentDescription = item.title
                )
            })
    }
}
