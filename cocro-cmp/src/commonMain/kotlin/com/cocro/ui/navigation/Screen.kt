package com.cocro.ui.navigation

sealed interface Screen {
    data object Landing : Screen
    data object Login : Screen
    data object Register : Screen
    data object Home : Screen
    data object LobbyCreate : Screen
    data class LobbyRoom(val shareCode: String) : Screen
    data class Game(val shareCode: String) : Screen
}
