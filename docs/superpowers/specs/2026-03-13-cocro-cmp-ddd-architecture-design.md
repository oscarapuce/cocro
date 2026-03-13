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
| Réutilisation domaine partagé | **Shared Kernel partiel** — emprunter enums, value objects stables et système d'erreur de `cocro-shared` ; définir les modèles client propres à `cocro-cmp` |
| Niveau DDD | **Intermédiaire** — Use Cases + Repository interfaces + mappers + séparation claire des couches |
| ViewModels | **Un ViewModel par feature** — `AuthViewModel`, `HomeViewModel`, `LobbyViewModel`, `GameViewModel` |
| Navigation | `MutableSharedFlow<NavigationEvent>` partagé entre tous les ViewModels, collecté dans `App.kt` |
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
│   │   └── SessionEvent.kt
│   ├── error/
│   │   ├── AuthError.kt
│   │   └── SessionError.kt
│   └── port/
│       ├── AuthRepository.kt
│       └── SessionRepository.kt
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
│   └── mapper/
│       ├── AuthMappers.kt
│       └── SessionMappers.kt
│   (network/ — http/ et stomp/ inchangés)
│
└── ui/
    ├── App.kt                         ← collecte NavigationEvent, route vers les screens
    ├── navigation/
    │   ├── Screen.kt                  ← inchangé
    │   └── NavigationEvent.kt
    ├── auth/
    │   ├── AuthViewModel.kt
    │   └── screens/                   ← LandingScreen, LoginScreen, RegisterScreen
    ├── home/
    │   ├── HomeViewModel.kt
    │   └── screens/                   ← HomeScreen
    ├── lobby/
    │   ├── LobbyViewModel.kt
    │   └── screens/                   ← LobbyCreateScreen, LobbyRoomScreen
    ├── game/
    │   ├── GameViewModel.kt
    │   └── screens/                   ← GameBoardScreen
    ├── components/                    ← inchangé
    └── theme/                         ← inchangé
```

---

## Couche Domaine

### Shared Kernel — ce qu'on emprunte de `cocro-shared`

| Élément | Usage |
|---|---|
| `SessionStatus` (enum) | Cycle de vie de session côté client |
| `CocroResult<T, E>` | Système d'erreur — retour des Repository et Use Cases |
| Erreurs domaine métier | Base pour `AuthError`, `SessionError` |

### Modèles client (`domain/model/`)

```kotlin
data class AuthUser(
    val userId: String,
    val username: String,
    val token: String,
    val isAnonymous: Boolean,
)

data class ClientSession(
    val sessionId: String,
    val shareCode: String,
    val status: SessionStatus,
    val participants: List<ClientParticipant>,
    val gridRevision: Long,
    val isCreator: Boolean,
)

data class ClientParticipant(
    val userId: String,
    val username: String,
    val isOnline: Boolean,
    val colorSlot: Int,           // 0..3 → palette curseur
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

enum class CellType { LETTER, BLACK, CLUE_H, CLUE_V, CLUE_HV }
```

### Erreurs (`domain/error/`)

```kotlin
sealed interface AuthError {
    data object InvalidCredentials : AuthError
    data object UsernameTaken : AuthError
    data class Unknown(val message: String) : AuthError
}

sealed interface SessionError {
    data object NotFound : SessionError
    data object AlreadyStarted : SessionError
    data object Unauthorized : SessionError
    data class Unknown(val message: String) : SessionError
}
```

### Ports (`domain/port/`)

```kotlin
interface AuthRepository {
    suspend fun login(username: String, password: String): CocroResult<AuthUser, AuthError>
    suspend fun register(username: String, password: String, email: String?): CocroResult<AuthUser, AuthError>
    suspend fun guest(): CocroResult<AuthUser, AuthError>
}

interface SessionRepository {
    suspend fun create(token: String, gridId: String): CocroResult<ClientSession, SessionError>
    suspend fun join(token: String, shareCode: String): CocroResult<ClientSession, SessionError>
    suspend fun leave(token: String, shareCode: String): CocroResult<Unit, SessionError>
    suspend fun start(token: String, shareCode: String): CocroResult<Unit, SessionError>
    suspend fun getState(token: String, shareCode: String): CocroResult<List<GridCell>, SessionError>
    fun observeEvents(token: String, shareCode: String): Flow<SessionEvent>
}
```

---

## Couche Application

Chaque Use Case est une classe avec `suspend operator fun invoke()` (ou `operator fun invoke()` pour les flows). Ils dépendent uniquement des interfaces de port — jamais des implémentations.

```kotlin
class LoginUseCase(private val authRepo: AuthRepository) {
    suspend operator fun invoke(
        username: String,
        password: String,
    ): CocroResult<AuthUser, AuthError> = authRepo.login(username, password)
}

class ObserveSessionEventsUseCase(private val sessionRepo: SessionRepository) {
    operator fun invoke(token: String, shareCode: String): Flow<SessionEvent> =
        sessionRepo.observeEvents(token, shareCode)
}
```

Les Use Cases fins (login, register, guest) délèguent directement au repository. Les Use Cases composés (ex: `PlaceLetterUseCase`) peuvent contenir de la logique de validation légère avant l'appel réseau.

---

## Couche Infrastructure

### Mappers (`infrastructure/mapper/`)

Fonctions d'extension qui convertissent DTOs réseau → modèles domaine client :

```kotlin
fun AuthResponse.toAuthUser(): AuthUser = AuthUser(
    userId = this.userId,
    username = this.username,
    token = this.token,
    isAnonymous = this.roles.contains("ANONYMOUS"),
)

fun ClientSessionEvent.toSessionEvent(): SessionEvent = when (this) {
    is ClientSessionEvent.Welcome -> SessionEvent.Welcome(...)
    is ClientSessionEvent.SessionStarted -> SessionEvent.SessionStarted
    // ...
}
```

### AuthRepositoryImpl

```kotlin
class AuthRepositoryImpl(private val authApiClient: AuthApiClient) : AuthRepository {
    override suspend fun login(username: String, password: String) =
        runCatching { authApiClient.login(username, password) }
            .fold(
                onSuccess = { CocroResult.Success(it.toAuthUser()) },
                onFailure = { CocroResult.Error(it.toAuthError()) },
            )
}
```

### SessionRepositoryImpl

```kotlin
class SessionRepositoryImpl(
    private val sessionApiClient: SessionApiClient,
    private val stompClientFactory: () -> StompClient,
    private val wsBaseUrl: String,
) : SessionRepository {
    override fun observeEvents(token: String, shareCode: String): Flow<SessionEvent> =
        SessionWebSocketClient(stompClientFactory(), wsBaseUrl)
            .connect(token, shareCode)
            .map { it.toSessionEvent() }
}
```

---

## Couche Présentation (MVI)

### Navigation

```kotlin
sealed interface NavigationEvent {
    data object ToLanding : NavigationEvent
    data object ToHome : NavigationEvent
    data class ToLobbyRoom(val shareCode: String) : NavigationEvent
    data class ToGame(val shareCode: String) : NavigationEvent
    data object Back : NavigationEvent
}
```

`App.kt` collecte le `SharedFlow<NavigationEvent>` et met à jour un `MutableState<Screen>`.

### ViewModels

```kotlin
class AuthViewModel(
    private val login: LoginUseCase,
    private val register: RegisterUseCase,
    private val guest: GuestLoginUseCase,
    val navigation: MutableSharedFlow<NavigationEvent>,
)

class HomeViewModel(
    val navigation: MutableSharedFlow<NavigationEvent>,
)

class LobbyViewModel(
    private val join: JoinSessionUseCase,
    private val create: CreateSessionUseCase,
    private val start: StartSessionUseCase,
    private val leave: LeaveSessionUseCase,
    val navigation: MutableSharedFlow<NavigationEvent>,
)

class GameViewModel(
    private val observeEvents: ObserveSessionEventsUseCase,
    private val placeLetter: PlaceLetterUseCase,
    private val clearCell: ClearCellUseCase,
    private val leave: LeaveSessionUseCase,
    val navigation: MutableSharedFlow<NavigationEvent>,
)
```

Chaque ViewModel expose un `StateFlow<UiState>` propre à sa feature.

---

## Wiring (Entry Points)

```kotlin
// MainActivity.kt
class MainActivity : ComponentActivity() {
    private val httpClient = buildHttpClient()
    private val authRepo = AuthRepositoryImpl(AuthApiClient(httpClient, BASE_URL))
    private val sessionRepo = SessionRepositoryImpl(
        sessionApiClient = SessionApiClient(httpClient, BASE_URL),
        stompClientFactory = { StompClient(buildHttpClient()) },
        wsBaseUrl = WS_BASE_URL,
    )
    private val navigation = MutableSharedFlow<NavigationEvent>(extraBufferCapacity = 8)

    private val authViewModel = AuthViewModel(
        login = LoginUseCase(authRepo),
        register = RegisterUseCase(authRepo),
        guest = GuestLoginUseCase(authRepo),
        navigation = navigation,
    )
    private val lobbyViewModel = LobbyViewModel(
        join = JoinSessionUseCase(sessionRepo),
        create = CreateSessionUseCase(sessionRepo),
        start = StartSessionUseCase(sessionRepo),
        leave = LeaveSessionUseCase(sessionRepo),
        navigation = navigation,
    )
    private val gameViewModel = GameViewModel(
        observeEvents = ObserveSessionEventsUseCase(sessionRepo),
        placeLetter = PlaceLetterUseCase(sessionRepo),
        clearCell = ClearCellUseCase(sessionRepo),
        leave = LeaveSessionUseCase(sessionRepo),
        navigation = navigation,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { App(authViewModel, lobbyViewModel, gameViewModel, navigation) }
    }
}
```

`MainViewController.kt` est identique avec les URLs iOS (`localhost:8080`).

---

## Ce qui ne change pas

- `network/http/` — `AuthApiClient`, `SessionApiClient`, `CocroHttpClient` inchangés
- `network/stomp/` — `StompClient`, `StompFrame` inchangés
- `session/SessionWebSocketClient.kt` — inchangé, utilisé par `SessionRepositoryImpl`
- `ui/components/` — tous les composants UI inchangés
- `ui/theme/` — inchangé
- `ui/navigation/Screen.kt` — inchangé

---

## Flux de données complet

```
Screen (Composable)
  │  action utilisateur (ex: bouton "Rejoindre")
  ▼
ViewModel.onJoin(shareCode)
  │  invoke
  ▼
JoinSessionUseCase(sessionRepo)
  │  call interface
  ▼
SessionRepositoryImpl  →  SessionApiClient (Ktor HTTP)
  │  CocroResult<ClientSession, SessionError>
  ▼
mapper DTO → ClientSession
  │
  ▼
ViewModel émet nouveau UiState  →  Screen se recompose
ViewModel émet NavigationEvent  →  App.kt navigue vers LobbyRoom
```

---

## Tests

- **Use Cases** : testables avec des fakes de `AuthRepository` / `SessionRepository` — aucune dépendance Ktor
- **ViewModels** : testables avec des Use Cases fakés — aucune dépendance Compose
- **RepositoryImpl** : testables avec un mock HTTP (MockEngine Ktor) ou un vrai serveur de test
