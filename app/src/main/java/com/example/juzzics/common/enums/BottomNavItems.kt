package com.example.juzzics.common.enums

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
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
    Library(
        title = "Library",
        selectedIcon = Icons.Filled.LibraryMusic,
        unSelectedIcon = Icons.Outlined.LibraryMusic,
        route = Screen.LibraryScreen.route
    ),
    Nearby(
        title = "Nearby",
        selectedIcon = Icons.Filled.WifiTethering,
        unSelectedIcon = Icons.Outlined.WifiTethering,
        route = Screen.NearbyScreen.route
    )
}
