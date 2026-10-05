package com.example.ui.navigation

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Pairing : Screen("pairing")
    data object Settings : Screen("settings")
    data object Privacy : Screen("privacy")
}
