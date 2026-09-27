package com.example.juzzics.common.enums

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.WifiTethering
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.juzzics.navigation.Screen

enum class BottomNavItems(
    val title: String,
    val selectedIcon: ImageVector,
    val unSelectedIcon: ImageVector,
    val route: String
) {
    Home(
        title = "Home",
        selectedIcon = Icons.Filled.Home,
        unSelectedIcon = Icons.Outlined.Home,
        route = Screen.HomeScreen.route
    ),
    Musics(
        title = "Musics",
        selectedIcon = Icons.Filled.Favorite,
        unSelectedIcon = Icons.Outlined.Favorite,
        route = Screen.MusicsScreen.route
    ),
    Playlists(
        title = "Playlists",
        selectedIcon = Icons.AutoMirrored.Filled.List,
        unSelectedIcon = Icons.AutoMirrored.Outlined.List,
        route = Screen.PlaylistsScreen.route
    ),
    Nearby(
        title = "Nearby",
        selectedIcon = Icons.Filled.WifiTethering,
        unSelectedIcon = Icons.Outlined.WifiTethering,
        route = Screen.NearbyScreen.route
    )
}
