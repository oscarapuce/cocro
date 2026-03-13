# Spec — Rearchitecture DDD de cocro-cmp

**Date:** 2026-03-13
**Statut:** Approuvé
**Scope:** `cocro-cmp` (Kotlin Multiplatform, Android + iOS)

---

## Contexte

L'architecture actuelle de `cocro-cmp` concentre toute la logique dans un `AppViewModel` God Object qui gère l'auth, la navigation, les sessions REST, le WebSocket et les mises à jour de grille. L'objectif est de rearchitecter en DDD intermédiaire compatible MVI, avec des ViewModels par feature et des Use Cases injectables.

---

## Décisions d'architecture

| Décision | Choix |
|---|---|
| Réutilisation domaine partagé | **Shared Kernel partiel** — emprunter `SessionStatus`, `CocroResult<T,E>` de `cocro-shared` ; définir les modèles et erreurs client propres à `cocro-cmp` |
| Niveau DDD | **Intermédiaire** — Use Cases + Repository interfaces + mappers + séparation claire des couches |
| ViewModels | **Un ViewModel par feature** — `AuthViewModel`, `HomeViewModel`, `LobbyViewModel`, `GameViewModel` |
| Navigation | `MutableSharedFlow<NavigationEvent>` partagé entre tous les ViewModels, collecté dans `App.kt` |
| Token persistence | `TokenStore` interface — implémenté en mémoire, injecté dans les ViewModels |
| DI | **Manuel** — wiring dans `MainActivity` / `MainViewController` (pas de framework DI, contrainte KMP) |

---

## Structure des packages

```
com.cocro/
│
├── domain/
│   ├── model/
│   │   ├── AuthUser.kt
│   │   ├── ClientSession.kt
│   │   ├── ClientParticipant.kt
│   │   ├── GridCell.kt
│   │   ├── CellType.kt
│   │   ├── WordDirection.kt           ← nouveau (était dans CocroKeyboard)
│   │   └── SessionEvent.kt
│   ├── error/
│   │   ├── AuthError.kt
│   │   └── SessionError.kt
│   └── port/
│       ├── AuthRepository.kt
│       ├── SessionRepository.kt
│       └── TokenStore.kt
│
├── application/
│   ├── auth/
│   │   ├── LoginUseCase.kt
│   │   ├── RegisterUseCase.kt
│   │   └── GuestLoginUseCase.kt
│   └── session/
│       ├── CreateSessionUseCase.kt
│       ├── JoinSessionUseCase.kt
│       ├── StartSessionUseCase.kt
│       ├── LeaveSessionUseCase.kt
│       ├── PlaceLetterUseCase.kt
│       ├── ClearCellUseCase.kt
│       └── ObserveSessionEventsUseCase.kt
│
├── infrastructure/
│   ├── auth/
│   │   └── AuthRepositoryImpl.kt
│   ├── session/
│   │   └── SessionRepositoryImpl.kt
│   ├── store/
│   │   └── InMemoryTokenStore.kt
│   └── mapper/
│       ├── AuthMappers.kt
│       └── SessionMappers.kt
│   (network/ — http/ et stomp/ inchangés)
│
└── ui/
    ├── App.kt                         ← MODIFIÉ — nouvelle signature, collecte NavigationEvent
    ├── navigation/
    │   ├── Screen.kt                  ← inchangé
    │   └── NavigationEvent.kt         ← nouveau
    ├── auth/
    │   ├── AuthViewModel.kt
    │   ├── AuthUiState.kt
    │   └── screens/                   ← LandingScreen, LoginScreen, RegisterScreen (déplacés depuis ui/landing/, ui/login/, ui/register/)
    ├── home/
    │   ├── HomeViewModel.kt
    │   └── screens/                   ← HomeScreen (déplacé depuis ui/home/)
    ├── lobby/
    │   ├── LobbyViewModel.kt
    │   ├── LobbyUiState.kt
    │   └── screens/                   ← LobbyCreateScreen, LobbyRoomScreen (déplacés depuis ui/lobby/)
    ├── game/
    │   ├── GameViewModel.kt
    │   ├── GameUiState.kt
    │   └── screens/                   ← GameBoardScreen (déplacé depuis ui/game/)
    ├── components/                    ← inchangé
    └── theme/                         ← inchangé
```

**Fichiers supprimés :** `session/AppViewModel.kt`, `session/SessionViewModel.kt`.
**Fichiers déplacés (contenu inchangé) :** tous les `*Screen.kt` vers leurs sous-dossiers `screens/`.
**`App.kt` :** signature modifiée — prend les 4 ViewModels + `navigation` au lieu de l'ancien `AppViewModel`.
**Ce qui reste intact :** `network/http/`, `network/stomp/`, `session/SessionWebSocketClient.kt`, `ui/components/`, `ui/theme/`, `ui/navigation/Screen.kt`.

---

## Couche Domaine

### Shared Kernel — ce qu'on emprunte de `cocro-shared`

| Élément | Usage |
|---|---|
| `SessionStatus` (enum) | Cycle de vie de session côté client |
| `CocroResult<T, E>` | Système d'erreur — retour des Repository et Use Cases |

Les erreurs domaine (`AuthError`, `SessionError`) sont des **sealed interfaces client indépendantes**. Les implémentations infrastructure mappent les exceptions réseau vers ces types via des fonctions d'extension.

### Modèles client (`domain/model/`)

```kotlin
data class AuthUser(
    val userId: String,
    val username: String,
    val token: String,
    val isAnonymous: Boolean,
)

// Représentation légère d'une session active — construite progressivement
// via le REST join/create, puis complétée par les événements WebSocket Welcome
data class ClientSession(
    val sessionId: String,
    val shareCode: String,
    val status: SessionStatus,
    val participantCount: Int,         // count issu de Welcome (pas de liste complète disponible côté client)
    val gridRevision: Long,
    val isCreator: Boolean,
)

data class ClientParticipant(
    val userId: String,
    val username: String,
    val isOnline: Boolean,
    val colorSlot: Int,               // 0..3 → palette curseur
    val cursorX: Int? = null,
    val cursorY: Int? = null,
)

data class GridCell(
    val x: Int,
    val y: Int,
    val type: CellType,
    val letter: Char? = null,
    val lockedBySlot: Int? = null,
)

// Enum client — valeurs alignées sur cocro-shared CellType pour simplifier le mapping
enum class CellType { LETTER, BLACK, CLUE_SINGLE, CLUE_DOUBLE }

// Direction de saisie dans la grille (défini en domaine client, déplacé depuis CocroKeyboard)
enum class WordDirection { HORIZONTAL, VERTICAL }
```

**Note sur `ClientSession.participantCount` :** le REST `join`/`create` retourne seulement `sessionId` + `shareCode` (ou `participantCount`). La liste complète des participants n'est pas disponible via REST — elle est construite dynamiquement dans le ViewModel à partir des événements WebSocket (`ParticipantJoined`, `ParticipantLeft`). `ClientSession` transporte donc un `participantCount: Int` issu du Welcome, pas une `List<ClientParticipant>`. La liste `participants: List<ClientParticipant>` vit dans `LobbyUiState.Active` et `GameUiState`, construite et maintenue par les ViewModels.

### SessionEvent (`domain/model/SessionEvent.kt`)

```kotlin
sealed interface SessionEvent {
    data class Welcome(
        val shareCode: String,
        val participantCount: Int,
        val status: SessionStatus,
        val gridRevision: Long,
    ) : SessionEvent

    data class ParticipantJoined(
        val userId: String,
        val participantCount: Int,
    ) : SessionEvent

    data class ParticipantLeft(
        val userId: String,
        val participantCount: Int,
        val reason: String,
    ) : SessionEvent

    data object SessionStarted : SessionEvent

    data class GridUpdated(
        val actorId: String,
        val x: Int,
        val y: Int,
        val commandType: String,      // "PLACE_LETTER" | "ERASE_LETTER"
        val letter: Char?,
    ) : SessionEvent

    data class SyncRequired(val currentRevision: Long) : SessionEvent

    data class Unknown(val type: String) : SessionEvent
}
```

**Note sur `topicToSubscribe` :** le champ `topicToSubscribe` présent dans `ClientSessionEvent.Welcome` (DTO réseau) est **consommé et géré en interne** par `SessionWebSocketClient` (abonnement au topic broadcast). Il n'est pas propagé au domaine — `SessionEvent.Welcome` ne le contient pas.

### Erreurs (`domain/error/`)

```kotlin
// AuthError.kt — sealed interface client, indépendant de cocro-shared
sealed interface AuthError {
    data object InvalidCredentials : AuthError
    data object UsernameTaken : AuthError
    data object WeakPassword : AuthError
    data class Unknown(val message: String) : AuthError
}

// SessionError.kt
sealed interface SessionError {
    data object NotFound : SessionError
    data object AlreadyStarted : SessionError
    data object Unauthorized : SessionError
    data class Unknown(val message: String) : SessionError
}
```

### Ports (`domain/port/`)

```kotlin
// AuthRepository.kt
interface AuthRepository {
    suspend fun login(username: String, password: String): CocroResult<AuthUser, AuthError>
    suspend fun register(username: String, password: String, email: String?): CocroResult<AuthUser, AuthError>
    suspend fun guest(): CocroResult<AuthUser, AuthError>
}

// SessionRepository.kt
interface SessionRepository {
    suspend fun create(token: String, gridId: String): CocroResult<ClientSession, SessionError>
    suspend fun join(token: String, shareCode: String): CocroResult<ClientSession, SessionError>
    suspend fun leave(token: String, shareCode: String): CocroResult<Unit, SessionError>
    suspend fun start(token: String, shareCode: String): CocroResult<Unit, SessionError>
    suspend fun getState(token: String, shareCode: String): CocroResult<List<GridCell>, SessionError>
    suspend fun placeLetter(token: String, shareCode: String, x: Int, y: Int, letter: Char): CocroResult<Unit, SessionError>
    suspend fun clearCell(token: String, shareCode: String, x: Int, y: Int): CocroResult<Unit, SessionError>
    fun observeEvents(token: String, shareCode: String, scope: CoroutineScope): Flow<SessionEvent>
}

// TokenStore.kt
interface TokenStore {
    fun save(user: AuthUser)
    fun get(): AuthUser?
    fun clear()
}
```

**Passage du token :** les ViewModels récupèrent le token depuis `TokenStore.get()` avant chaque appel aux Use Cases. `TokenStore` est mis à jour par `AuthViewModel` après login/register/guest, et vidé par `AuthViewModel` lors du logout.

---

## Couche Application

```kotlin
class LoginUseCase(private val authRepo: AuthRepository) {
    suspend operator fun invoke(username: String, password: String): CocroResult<AuthUser, AuthError> =
        authRepo.login(username, password)
}

class JoinSessionUseCase(private val sessionRepo: SessionRepository) {
    suspend operator fun invoke(token: String, shareCode: String): CocroResult<ClientSession, SessionError> =
        sessionRepo.join(token, shareCode)
}

class PlaceLetterUseCase(private val sessionRepo: SessionRepository) {
    suspend operator fun invoke(token: String, shareCode: String, x: Int, y: Int, letter: Char): CocroResult<Unit, SessionError> =
        sessionRepo.placeLetter(token, shareCode, x, y, letter)
}

class ClearCellUseCase(private val sessionRepo: SessionRepository) {
    suspend operator fun invoke(token: String, shareCode: String, x: Int, y: Int): CocroResult<Unit, SessionError> =
        sessionRepo.clearCell(token, shareCode, x, y)
}

class ObserveSessionEventsUseCase(private val sessionRepo: SessionRepository) {
    operator fun invoke(token: String, shareCode: String, scope: CoroutineScope): Flow<SessionEvent> =
        sessionRepo.observeEvents(token, shareCode, scope)
}
```

Les autres Use Cases (Register, Guest, Create, Start, Leave) suivent le même pattern de délégation directe.

---

## Couche Infrastructure

### Mappers (`infrastructure/mapper/`)

```kotlin
// AuthMappers.kt
// AuthResponse = com.cocro.network.dto.AuthResponse (existant : userId, username, roles: Set<String>, token)
fun AuthResponse.toAuthUser(): AuthUser = AuthUser(
    userId = this.userId,
    username = this.username,
    token = this.token,
    isAnonymous = this.roles.contains("ANONYMOUS"),
)

fun Throwable.toAuthError(): AuthError = when {
    message?.contains("401") == true -> AuthError.InvalidCredentials
    message?.contains("409") == true -> AuthError.UsernameTaken
    else -> AuthError.Unknown(message ?: "Erreur inconnue")
}

// SessionMappers.kt
// JoinSessionResponse / CreateSessionResponse : sessionId + shareCode (+ participantCount pour join)
// ClientSession construit avec valeurs par défaut : status=CREATING, participantCount=0, gridRevision=0
// Ces valeurs sont complétées par le ViewModel lors du SessionEvent.Welcome
fun JoinSessionResponse.toClientSession(isCreator: Boolean): ClientSession = ClientSession(
    sessionId = this.sessionId,
    shareCode = "unknown",              // non fourni par le REST join — sera mis à jour via Welcome
    status = SessionStatus.CREATING,
    participantCount = this.participantCount,
    gridRevision = 0L,
    isCreator = isCreator,
)
// Note : le shareCode est passé en paramètre de join → le ViewModel le connaît déjà
// → variante recommandée : fun JoinSessionResponse.toClientSession(shareCode: String, isCreator: Boolean)

fun ClientSessionEvent.toSessionEvent(): SessionEvent = when (this) {
    is ClientSessionEvent.Welcome -> SessionEvent.Welcome(this.shareCode, this.participantCount, this.status, this.gridRevision)
    is ClientSessionEvent.SessionStarted -> SessionEvent.SessionStarted
    is ClientSessionEvent.ParticipantJoined -> SessionEvent.ParticipantJoined(this.userId, this.participantCount)
    is ClientSessionEvent.ParticipantLeft -> SessionEvent.ParticipantLeft(this.userId, this.participantCount, this.reason)
    is ClientSessionEvent.GridUpdated -> SessionEvent.GridUpdated(this.actorId, this.posX, this.posY, this.commandType, this.letter)
    is ClientSessionEvent.SyncRequired -> SessionEvent.SyncRequired(this.currentRevision)
    is ClientSessionEvent.Unknown -> SessionEvent.Unknown(this.type)
}
```

### SessionRepositoryImpl

```kotlin
class SessionRepositoryImpl(
    private val sessionApiClient: SessionApiClient,
    private val stompClientFactory: () -> StompClient,
    private val wsBaseUrl: String,
) : SessionRepository {

    override fun observeEvents(token: String, shareCode: String, scope: CoroutineScope): Flow<SessionEvent> {
        // SessionWebSocketClient API existante : prend stompClient, wsBaseUrl, token, shareCode, scope
        val wsClient = SessionWebSocketClient(
            stompClient = stompClientFactory(),
            wsBaseUrl = wsBaseUrl,
            token = token,
            shareCode = shareCode,
            scope = scope,
        )
        // SessionWebSocketClient gère en interne l'abonnement STOMP (queue privée + topic broadcast)
        // topicToSubscribe du Welcome est consommé à ce niveau, pas propagé au domaine
        return wsClient.events.map { it.toSessionEvent() }
    }

    override suspend fun join(token: String, shareCode: String) =
        runCatching { sessionApiClient.join(token, shareCode) }
            .fold(
                onSuccess = { CocroResult.Success(it.toClientSession(shareCode = shareCode, isCreator = false)) },
                onFailure = { CocroResult.Error(it.toSessionError()) },
            )

    override suspend fun placeLetter(token: String, shareCode: String, x: Int, y: Int, letter: Char): CocroResult<Unit, SessionError> {
        // placeLetter et clearCell sont des commandes WebSocket (STOMP SEND), pas des appels HTTP
        // SessionWebSocketClient.sendGridUpdate() est fire-and-forget (suspend Unit)
        // Le résultat est confirmé indirectement via SessionEvent.GridUpdated
        // → CocroResult.Success(Unit) immédiatement après l'envoi STOMP
        // → Pour accéder au wsClient, SessionRepositoryImpl maintient une référence au wsClient actif
        return CocroResult.Success(Unit) // détail d'implémentation : voir note ci-dessous
    }

    // create, leave, start, getState : runCatching + fold, même pattern que join
}
```

**Note sur `placeLetter`/`clearCell` :** ces commandes passent par WebSocket (STOMP SEND via `SessionWebSocketClient.sendGridUpdate()`), pas par HTTP. `SessionRepositoryImpl` doit maintenir une référence au `SessionWebSocketClient` actif (créé lors du premier appel à `observeEvents`). Solution : stocker le `wsClient` dans un champ nullable initialisé lors de `observeEvents`, et le réutiliser pour `placeLetter`/`clearCell`.

### AuthRepositoryImpl

```kotlin
class AuthRepositoryImpl(private val authApiClient: AuthApiClient) : AuthRepository {
    override suspend fun login(username: String, password: String) =
        runCatching { authApiClient.login(username, password) }
            .fold(
                onSuccess = { CocroResult.Success(it.toAuthUser()) },
                onFailure = { CocroResult.Error(it.toAuthError()) },
            )
    // register et guest : même pattern
}
```

### InMemoryTokenStore

```kotlin
class InMemoryTokenStore : TokenStore {
    private var current: AuthUser? = null
    override fun save(user: AuthUser) { current = user }
    override fun get(): AuthUser? = current
    override fun clear() { current = null }
}
```

---

## Couche Présentation (MVI)

### NavigationEvent

```kotlin
sealed interface NavigationEvent {
    data object ToLanding : NavigationEvent
    data object ToHome : NavigationEvent
    data object ToLobbyCreate : NavigationEvent
    data class ToLobbyRoom(val shareCode: String) : NavigationEvent
    data class ToGame(val shareCode: String) : NavigationEvent
    data object Back : NavigationEvent
}
```

`App.kt` collecte le `SharedFlow<NavigationEvent>` dans un `LaunchedEffect` et met à jour un `MutableState<Screen>`.

### UiState par feature

```kotlin
// AuthUiState.kt
sealed interface AuthUiState {
    data object Idle : AuthUiState
    data object Loading : AuthUiState
    data class Error(val message: String) : AuthUiState
}

// LobbyUiState.kt
sealed interface LobbyUiState {
    data object Idle : LobbyUiState
    data object Loading : LobbyUiState
    // Active construit après le SessionEvent.Welcome — pas après le REST join
    data class Active(
        val shareCode: String,
        val status: SessionStatus,
        val participantCount: Int,
        val participants: List<ClientParticipant>,  // construite via ParticipantJoined/Left events
        val isCreator: Boolean,
    ) : LobbyUiState
    data class Error(val message: String) : LobbyUiState
}

// GameUiState.kt
data class GameUiState(
    val shareCode: String = "",
    val revision: Long = 0,
    val participants: List<ClientParticipant> = emptyList(),
    val cells: List<GridCell> = emptyList(),
    val selectedX: Int = 0,
    val selectedY: Int = 0,
    val direction: WordDirection = WordDirection.HORIZONTAL,  // WordDirection défini en domain/model/
    val isLoading: Boolean = false,
    val error: String? = null,
)
```

**Séquence de transitions LobbyViewModel :**
1. `onJoin(shareCode)` → `uiState = Loading` → appel REST join → succès → démarre `observeEvents`
2. `SessionEvent.Welcome` reçu → `uiState = Active(shareCode, status, participantCount, emptyList(), isCreator)`
3. `SessionEvent.ParticipantJoined` → met à jour `participants` (ajoute un `ClientParticipant` avec `userId`, `colorSlot` calculé selon l'ordre d'arrivée)
4. `SessionEvent.SessionStarted` → `navigation.emit(ToGame(shareCode))`

**Note :** `HomeViewModel` n'a pas de `UiState` — il est **pur navigation**. Il expose `val username: String` calculé depuis `tokenStore.get()?.username`.

### ViewModels

```kotlin
class AuthViewModel(
    private val login: LoginUseCase,
    private val register: RegisterUseCase,
    private val guest: GuestLoginUseCase,
    private val tokenStore: TokenStore,
    val navigation: MutableSharedFlow<NavigationEvent>,
) {
    val uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    // Après succès → tokenStore.save(user) + navigation.emit(ToHome)
    // Après erreur → uiState = Error(message)
}

class HomeViewModel(
    private val tokenStore: TokenStore,
    val navigation: MutableSharedFlow<NavigationEvent>,
) {
    val username: String get() = tokenStore.get()?.username ?: ""
    // Actions : onCreateSession() → ToLobbyCreate, onJoin(code) → ToLobbyRoom(code), onLogout() → tokenStore.clear() + ToLanding
}

class LobbyViewModel(
    private val join: JoinSessionUseCase,
    private val create: CreateSessionUseCase,
    private val start: StartSessionUseCase,
    private val leave: LeaveSessionUseCase,
    private val observeEvents: ObserveSessionEventsUseCase,
    private val tokenStore: TokenStore,
    val navigation: MutableSharedFlow<NavigationEvent>,
) {
    val uiState = MutableStateFlow<LobbyUiState>(LobbyUiState.Idle)
    // observeEvents démarre dans une coroutine interne après join/create
    // SessionStarted → navigation.emit(ToGame(shareCode))
}

class GameViewModel(
    private val observeEvents: ObserveSessionEventsUseCase,
    private val placeLetter: PlaceLetterUseCase,
    private val clearCell: ClearCellUseCase,
    private val leave: LeaveSessionUseCase,
    private val tokenStore: TokenStore,
    val navigation: MutableSharedFlow<NavigationEvent>,
) {
    val uiState = MutableStateFlow(GameUiState())
    // GridUpdated → met à jour cells dans uiState
    // SyncRequired → appelle getState via sessionRepo pour rehydrater
}
```

---

## Wiring (Entry Points)

```kotlin
// MainActivity.kt (Android) — MODIFIÉ par rapport à l'existant
class MainActivity : ComponentActivity() {
    private val httpClient = buildHttpClient()
    private val tokenStore = InMemoryTokenStore()
    private val authRepo = AuthRepositoryImpl(AuthApiClient(httpClient, BASE_URL))
    private val sessionRepo = SessionRepositoryImpl(
        sessionApiClient = SessionApiClient(httpClient, BASE_URL),
        stompClientFactory = { StompClient(buildHttpClient()) },
        wsBaseUrl = WS_BASE_URL,
    )
    private val navigation = MutableSharedFlow<NavigationEvent>(extraBufferCapacity = 8)

    private val authViewModel = AuthViewModel(LoginUseCase(authRepo), RegisterUseCase(authRepo), GuestLoginUseCase(authRepo), tokenStore, navigation)
    private val homeViewModel = HomeViewModel(tokenStore, navigation)
    private val lobbyViewModel = LobbyViewModel(JoinSessionUseCase(sessionRepo), CreateSessionUseCase(sessionRepo), StartSessionUseCase(sessionRepo), LeaveSessionUseCase(sessionRepo), ObserveSessionEventsUseCase(sessionRepo), tokenStore, navigation)
    private val gameViewModel = GameViewModel(ObserveSessionEventsUseCase(sessionRepo), PlaceLetterUseCase(sessionRepo), ClearCellUseCase(sessionRepo), LeaveSessionUseCase(sessionRepo), tokenStore, navigation)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // App.kt nouvelle signature : App(authViewModel, homeViewModel, lobbyViewModel, gameViewModel, navigation)
        setContent { App(authViewModel, homeViewModel, lobbyViewModel, gameViewModel, navigation) }
    }
}
```

`MainViewController.kt` : même structure, URLs iOS (`http://localhost:8080`, `ws://localhost:8080`).

---

## Flux de données complet

```
Screen (Composable)
  │  action utilisateur
  ▼
ViewModel  ──  token = tokenStore.get()?.token
  │            invoke UseCase(token, ...)
  ▼
UseCase  ──  délègue à Repository interface
  ▼
RepositoryImpl  ──  Ktor HTTP ou STOMP WS
  │  mapper DTO → modèle domaine client
  ▼
CocroResult<T, E>  ou  Flow<SessionEvent>
  │
  ▼
ViewModel : met à jour StateFlow<UiState>  →  Screen se recompose
ViewModel : émet NavigationEvent          →  App.kt change Screen
```

---

## Ce qui ne change pas

| Fichier / Package | Raison |
|---|---|
| `network/http/` (AuthApiClient, SessionApiClient, CocroHttpClient) | Wrappers Ktor stables |
| `network/stomp/` (StompClient, StompFrame) | Protocole STOMP inchangé |
| `session/SessionWebSocketClient.kt` | API inchangée, réutilisée par `SessionRepositoryImpl` |
| `ui/components/` | Composants UI inchangés |
| `ui/theme/` | Design system inchangé |
| `ui/navigation/Screen.kt` | Scellé inchangé |

---

## Tests

- **Use Cases** : testables avec fakes de `AuthRepository` / `SessionRepository` — aucune dépendance Ktor
- **ViewModels** : testables avec Use Cases fakés + `InMemoryTokenStore` — aucune dépendance Compose
- **RepositoryImpl** : testables avec `MockEngine` Ktor ou un vrai serveur de test
- **TokenStore** : `InMemoryTokenStore` utilisable directement dans les tests, pas de mock nécessaire
