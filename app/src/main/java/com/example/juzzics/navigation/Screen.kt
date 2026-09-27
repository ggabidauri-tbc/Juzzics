package com.example.juzzics.navigation

sealed class Screen(val route: String) {
    data object HomeScreen : Screen("HomeScreen")
    data object MusicsScreen : Screen("MusicsScreen")
    data object PlaylistsScreen : Screen("PlaylistsScreen")
    data object FetchLyricsScreen : Screen("FetchLyricsScreen")
}
