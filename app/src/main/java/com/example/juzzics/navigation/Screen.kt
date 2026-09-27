package com.example.juzzics.navigation

sealed class Screen(val route: String) {
    data object HomeScreen : Screen("HomeScreen")
    data object LibraryScreen : Screen("LibraryScreen")
    data object NearbyScreen : Screen("NearbyScreen")
}
