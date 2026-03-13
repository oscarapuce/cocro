package com.cocro.session

import com.cocro.network.dto.AuthResponse
import com.cocro.network.dto.ClientSessionEvent
import com.cocro.network.http.AuthApiClient
import com.cocro.network.http.SessionApiClient
import com.cocro.network.stomp.StompClient
import com.cocro.ui.navigation.Screen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Top-level ViewModel that owns navigation state in addition to all session/auth logic.
 * Replaces SessionViewModel as the single ViewModel used by the app entry points.
 */
class AppViewModel(
    private val authApiClient: AuthApiClient,
    private val sessionApiClient: SessionApiClient,
    private val stompClientFactory: () -> StompClient,
    private val wsBaseUrl: String,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // -------------------------------------------------------------------------
    // Auth
    // -------------------------------------------------------------------------

    private val _auth = MutableStateFlow<AuthResponse?>(null)
    val auth: StateFlow<AuthResponse?> = _auth.asStateFlow()

    // -------------------------------------------------------------------------
    // Navigation
    // -------------------------------------------------------------------------

    private val _currentScreen = MutableStateFlow<Screen>(Screen.Landing)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    fun navigateTo(screen: Screen) {
        _currentScreen.value = screen
    }

    // -------------------------------------------------------------------------
    // Session state
    // -------------------------------------------------------------------------

    private val _sessionState = MutableStateFlow<SessionUiState>(SessionUiState.Idle)
    val sessionState: StateFlow<SessionUiState> = _sessionState.asStateFlow()

    private var wsClient: SessionWebSocketClient? = null

    // -------------------------------------------------------------------------
    // Auth operations
    // -------------------------------------------------------------------------

    fun login(username: String, password: String) {
        scope.launch {
            _sessionState.value = SessionUiState.Loading("Connexion…")
            runCatching { authApiClient.login(username, password) }
                .onSuccess { response ->
                    _auth.value = response
                    _sessionState.value = SessionUiState.Idle
                    _currentScreen.value = Screen.Home
                }
                .onFailure { _sessionState.value = SessionUiState.Error("Échec de la connexion : ${it.message}") }
        }
    }

    fun register(username: String, password: String, email: String?) {
        scope.launch {
            _sessionState.value = SessionUiState.Loading("Création du compte…")
            runCatching { authApiClient.register(username, password, email) }
                .onSuccess { response ->
                    _auth.value = response
                    _sessionState.value = SessionUiState.Idle
                    _currentScreen.value = Screen.Home
                }
                .onFailure { _sessionState.value = SessionUiState.Error("Échec de l'inscription : ${it.message}") }
        }
    }

    fun logout() {
        disconnectWebSocket()
        _auth.value = null
        _sessionState.value = SessionUiState.Idle
        _currentScreen.value = Screen.Landing
    }

    // -------------------------------------------------------------------------
    // Guest flow
    // -------------------------------------------------------------------------

    /**
     * Obtain an anonymous token then join the given session directly.
     * The user lands on LobbyRoom after a successful join.
     */
    fun joinAsGuest(shareCode: String) {
        scope.launch {
            _sessionState.value = SessionUiState.Loading("Connexion en tant qu'invité…")
            runCatching { authApiClient.guest() }
                .onSuccess { response ->
                    _auth.value = response
                    // Now join the session with the guest token
                    runCatching { sessionApiClient.join(response.token, shareCode) }
                        .onSuccess {
                            connectWebSocket(response.token, shareCode, response.userId, isCreator = false)
                            _currentScreen.value = Screen.LobbyRoom(shareCode)
                        }
                        .onFailure { _sessionState.value = SessionUiState.Error("Impossible de rejoindre : ${it.message}") }
                }
                .onFailure { _sessionState.value = SessionUiState.Error("Connexion invité échouée : ${it.message}") }
        }
    }

    // -------------------------------------------------------------------------
    // Session lifecycle (REST)
    // -------------------------------------------------------------------------

    fun createSession(gridCode: String) {
        val token = _auth.value?.token ?: return
        val userId = _auth.value?.userId ?: return
        scope.launch {
            _sessionState.value = SessionUiState.Loading("Création de la session…")
            runCatching { sessionApiClient.create(token, gridCode) }
                .onSuccess { resp ->
                    connectWebSocket(token, resp.shareCode, userId, isCreator = true)
                    _currentScreen.value = Screen.LobbyRoom(resp.shareCode)
                }
                .onFailure { _sessionState.value = SessionUiState.Error("Création échouée : ${it.message}") }
        }
    }

    fun joinSession(shareCode: String) {
        val token = _auth.value?.token ?: return
        val userId = _auth.value?.userId ?: return
        scope.launch {
            _sessionState.value = SessionUiState.Loading("Connexion à la session…")
            runCatching { sessionApiClient.join(token, shareCode) }
                .onSuccess {
                    connectWebSocket(token, shareCode, userId, isCreator = false)
                    _currentScreen.value = Screen.LobbyRoom(shareCode)
                }
                .onFailure { _sessionState.value = SessionUiState.Error("Impossible de rejoindre : ${it.message}") }
        }
    }

    fun startSession() {
        val token = _auth.value?.token ?: return
        val state = _sessionState.value as? SessionUiState.Active ?: return
        scope.launch {
            runCatching { sessionApiClient.start(token, state.shareCode) }
                .onFailure { _sessionState.value = SessionUiState.Error("Démarrage échoué : ${it.message}") }
            // Navigation to Game happens via SessionStarted WebSocket event
        }
    }

    fun leaveSession() {
        val token = _auth.value?.token ?: return
        val state = _sessionState.value as? SessionUiState.Active ?: return
        val shareCode = state.shareCode
        scope.launch {
            runCatching { sessionApiClient.leave(token, shareCode) }
            disconnectWebSocket()
            val isAnonymous = _auth.value?.roles?.contains("ANONYMOUS") == true
            if (isAnonymous) {
                _auth.value = null
                _currentScreen.value = Screen.Landing
            } else {
                _currentScreen.value = Screen.Home
            }
        }
    }

    fun quitSession() = disconnectWebSocket()

    // -------------------------------------------------------------------------
    // WebSocket
    // -------------------------------------------------------------------------

    private fun connectWebSocket(token: String, shareCode: String, userId: String, isCreator: Boolean) {
        wsClient?.disconnect()

        val stompClient = stompClientFactory()
        val client = SessionWebSocketClient(stompClient, wsBaseUrl, token, shareCode, scope)
        wsClient = client

        _sessionState.value = SessionUiState.Loading("Connexion WebSocket…")

        scope.launch {
            client.events.collect { event -> handleEvent(event, shareCode, userId, isCreator) }
        }
    }

    private fun disconnectWebSocket() {
        wsClient?.disconnect()
        wsClient = null
        _sessionState.value = SessionUiState.Idle
    }

    // -------------------------------------------------------------------------
    // Event handling
    // -------------------------------------------------------------------------

    private fun handleEvent(event: ClientSessionEvent, shareCode: String, userId: String, isCreator: Boolean) {
        when (event) {
            is ClientSessionEvent.Welcome -> {
                val status = event.status.toSessionStatus()
                _sessionState.value = SessionUiState.Active(
                    sessionId = "",
                    shareCode = event.shareCode,
                    status = status,
                    participants = List(event.participantCount) { Participant("participant-$it") },
                    gridRevision = event.gridRevision,
                    cells = emptyList(),
                    isCreator = isCreator,
                )
                // Navigate based on session status received in welcome
                when (status) {
                    SessionStatus.PLAYING -> _currentScreen.value = Screen.Game(event.shareCode)
                    else -> _currentScreen.value = Screen.LobbyRoom(event.shareCode)
                }
            }

            is ClientSessionEvent.ParticipantJoined -> updateActive { active ->
                val newParticipant = Participant(event.userId)
                active.copy(participants = (active.participants + newParticipant).takeLast(event.participantCount))
            }

            is ClientSessionEvent.ParticipantLeft -> updateActive { active ->
                active.copy(participants = active.participants.filter { it.userId != event.userId })
            }

            is ClientSessionEvent.SessionStarted -> {
                updateActive { active -> active.copy(status = SessionStatus.PLAYING) }
                // Navigate to game board
                val activeState = _sessionState.value as? SessionUiState.Active
                if (activeState != null) {
                    _currentScreen.value = Screen.Game(activeState.shareCode)
                }
            }

            is ClientSessionEvent.GridUpdated -> updateActive { active ->
                active.copy(gridRevision = active.gridRevision + 1)
            }

            is ClientSessionEvent.SyncRequired -> resync()

            is ClientSessionEvent.Unknown -> println("[WS] Unknown event type: ${event.type}")
        }
    }

    private fun resync() {
        val token = _auth.value?.token ?: return
        val state = _sessionState.value as? SessionUiState.Active ?: return
        scope.launch {
            runCatching { sessionApiClient.getState(token, state.shareCode) }
                .onSuccess { resp ->
                    updateActive { it.copy(gridRevision = resp.revision, cells = resp.cells) }
                }
        }
    }

    // -------------------------------------------------------------------------
    // Grid updates (outbound)
    // -------------------------------------------------------------------------

    fun placeLetter(posX: Int, posY: Int, letter: Char) =
        wsClient?.sendGridUpdate(posX, posY, "PLACE_LETTER", letter)

    fun clearCell(posX: Int, posY: Int) =
        wsClient?.sendGridUpdate(posX, posY, "ERASE_LETTER")

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun updateActive(transform: (SessionUiState.Active) -> SessionUiState.Active) {
        _sessionState.update { state ->
            if (state is SessionUiState.Active) transform(state) else state
        }
    }

    private fun String.toSessionStatus(): SessionStatus = runCatching {
        SessionStatus.valueOf(this)
    }.getOrDefault(SessionStatus.CREATING)

    fun onCleared() {
        wsClient?.disconnect()
        scope.cancel()
    }
}
