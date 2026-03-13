package com.cocro.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.cocro.session.AppViewModel
import com.cocro.ui.game.GameBoardScreen
import com.cocro.ui.home.HomeScreen
import com.cocro.ui.landing.LandingScreen
import com.cocro.ui.lobby.LobbyCreateScreen
import com.cocro.ui.lobby.LobbyRoomScreen
import com.cocro.ui.login.LoginScreen
import com.cocro.ui.navigation.Screen
import com.cocro.ui.register.RegisterScreen
import com.cocro.ui.theme.CocroTheme

@Composable
fun App(viewModel: AppViewModel) {
    val screen by viewModel.currentScreen.collectAsState()
    val sessionState by viewModel.sessionState.collectAsState()
    val auth by viewModel.auth.collectAsState()

    CocroTheme {
        when (val s = screen) {
            Screen.Landing -> LandingScreen(
                isAnonymous = auth != null && (auth?.roles?.contains("ANONYMOUS") == true),
                guestUsername = if (auth?.roles?.contains("ANONYMOUS") == true) auth?.username else null,
                onJoinAsGuest = viewModel::joinAsGuest,
                onJoinAuthenticated = viewModel::joinSession,
                onNavigateLogin = { viewModel.navigateTo(Screen.Login) },
                onNavigateRegister = { viewModel.navigateTo(Screen.Register) },
                sessionState = sessionState,
            )

            Screen.Login -> LoginScreen(
                onLogin = viewModel::login,
                onNavigateRegister = { viewModel.navigateTo(Screen.Register) },
                onNavigateLanding = { viewModel.navigateTo(Screen.Landing) },
                sessionState = sessionState,
            )

            Screen.Register -> RegisterScreen(
                onRegister = viewModel::register,
                onNavigateLogin = { viewModel.navigateTo(Screen.Login) },
                onNavigateLanding = { viewModel.navigateTo(Screen.Landing) },
                sessionState = sessionState,
            )

            Screen.Home -> HomeScreen(
                username = auth?.username ?: "",
                isAnonymous = false,
                onJoinSession = viewModel::joinSession,
                onCreateSession = { viewModel.navigateTo(Screen.LobbyCreate) },
                // TODO: navigate to Screen.GridEditor once implemented
                onCreateGrid = { viewModel.navigateTo(Screen.LobbyCreate) },
                onLogout = viewModel::logout,
                sessionState = sessionState,
            )

            Screen.LobbyCreate -> LobbyCreateScreen(
                onCreateSession = viewModel::createSession,
                onBack = { viewModel.navigateTo(Screen.Home) },
                sessionState = sessionState,
            )

            is Screen.LobbyRoom -> LobbyRoomScreen(
                shareCode = s.shareCode,
                sessionState = sessionState,
                onStartSession = viewModel::startSession,
                onLeaveSession = viewModel::leaveSession,
            )

            is Screen.Game -> GameBoardScreen(
                shareCode = s.shareCode,
                myUserId = auth?.userId ?: "",
                sessionState = sessionState,
                onPlaceLetter = viewModel::placeLetter,
                onClearCell = viewModel::clearCell,
                onLeave = viewModel::leaveSession,
            )
        }
    }
}
