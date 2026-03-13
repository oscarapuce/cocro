# Compose Multiplatform (KMP) Reference

## Stack

- Kotlin Multiplatform: Android (min SDK 26) + iOS (arm64, x64, simulatorArm64)
- Compose Multiplatform for UI (shared composables across Android and iOS)
- Ktor 2.3.6 for HTTP + WebSocket
- Custom STOMP client (`StompClient.kt`) over Ktor WebSocket
- Kotlinx Serialization for JSON

---

## MVI Architecture

The app follows a Model-View-Intent (MVI) pattern:

```
┌────────────────────────────────────────────────────────────────┐
│                         AppViewModel                           │
│                                                                │
│  Intents (user actions):        State (model):                 │
│  viewModel.login(u, p)    ──►   auth: StateFlow<AuthResponse?> │
│  viewModel.joinSession(c) ──►   sessionState: StateFlow<...>   │
│  viewModel.placeLetter()  ──►   currentScreen: StateFlow<...>  │
│                                                                │
│  Screens collect state via .collectAsState() and call          │
│  ViewModel methods directly for intents.                       │
└────────────────────────────────────────────────────────────────┘
```

**Current state**: `AppViewModel` acts as the single intent processor. Screens are pure composables that receive state and callbacks — they hold no business logic.

**Target MVI refinement** (for future extraction):

```kotlin
sealed interface SessionIntent {
    data class Join(val shareCode: String) : SessionIntent
    data class PlaceLetter(val x: Int, val y: Int, val letter: Char) : SessionIntent
    data class ClearCell(val x: Int, val y: Int) : SessionIntent
    data object Leave : SessionIntent
}
// ViewModel exposes: fun dispatch(intent: SessionIntent)
// Screens call: viewModel.dispatch(SessionIntent.PlaceLetter(x, y, 'A'))
```

---

## Navigation

`Screen` is a sealed interface. `AppViewModel` holds `_currentScreen: MutableStateFlow<Screen>`. `App.kt` collects `currentScreen` and switches between composables. There is no Navigation Compose — single activity, manual screen stack.

```kotlin
sealed interface Screen {
    object Landing : Screen
    object Login : Screen
    object Register : Screen
    object Home : Screen
    object LobbyCreate : Screen
    data class LobbyRoom(val shareCode: String) : Screen
    data class Game(val shareCode: String) : Screen
}
```

Navigation is imperative: `viewModel.navigate(Screen.Game(shareCode))` sets `_currentScreen.value`.

---

## State Model

### SessionUiState

```kotlin
sealed interface SessionUiState {
    object Idle : SessionUiState

    data class Loading(val message: String) : SessionUiState

    data class Active(
        val sessionId: String,
        val shareCode: String,
        val status: SessionStatus,
        val participants: List<Participant>,
        val gridRevision: Long,
        val cells: List<CellStateDto>,
        val isCreator: Boolean,
    ) : SessionUiState

    data class Error(val message: String) : SessionUiState
}
```

### Participant

```kotlin
data class Participant(
    val userId: String,
    val username: String = "",
    val isOnline: Boolean = true,
    val cursorX: Int? = null,   // other players' cursor X (for multi-player cursor display)
    val cursorY: Int? = null,   // other players' cursor Y
)
```

---

## Custom Keyboard

`CocroKeyboard` is a full custom composable (AZERTY layout) that completely replaces the system keyboard. It never triggers `BasicTextField`, so no phone keyboard appears during gameplay.

- Keys are `Box` composables with click handlers
- Layout: 3 rows — `[A][Z][E][R][T][Y][U][I][O][P]` / `[Q][S][D][F][G][H][J][K][L][M]` / `[→][W][X][C][V][B][N][⌫]`
- Direction key (`→` / `↓`) toggles `WordDirection` (HORIZONTAL / VERTICAL)
- Double-tap on the selected cell toggles direction without a key press
- After each letter input, the selected cell advances in the current `WordDirection`

```kotlin
enum class WordDirection { HORIZONTAL, VERTICAL }
```

---

## Networking

### HTTP clients

| Class | Purpose |
|-------|---------|
| `CocroHttpClient` | Ktor `HttpClient` with `ContentNegotiation(Json)` + `Logging` |
| `AuthApiClient` | `POST /auth/login`, `POST /auth/register`, `POST /auth/guest` |
| `SessionApiClient` | All session REST endpoints (create, join, leave, start, state, check) |

Base URL is injected at build time per platform (Android: `http://10.0.2.2:8080`, iOS: `http://localhost:8080`, prod: configured).

### WebSocket

| Class | Purpose |
|-------|---------|
| `StompClient` | Custom STOMP frame serializer/deserializer over Ktor `WebSocketSession` |
| `SessionWebSocketClient` | Manages full STOMP lifecycle for one session: connect, subscribe, send, disconnect |

`StompClient` sends CONNECT with `Authorization: Bearer $token` header in STOMP headers (not HTTP headers — those are set at the Ktor WebSocket upgrade level).

Subscribe pattern mirrors the Angular client:
1. Connect
2. In `onConnected`: subscribe to `/app/session/{code}/welcome` → receive `SessionWelcome`
3. Subscribe to `/topic/session/{code}` for broadcasts
4. Subscribe to `/user/queue/session` for private events

---

## Design System

### Typography (`CocroTypography.kt`)

Design: **Fusion B — Cahier de Notes** — see `docs/design/option-2.md`.

| Token | Font | Usage |
|-------|------|-------|
| `FontTitle` | Space Grotesk 700 | Section titles, screen headers |
| `FontHand` | Patrick Hand (Cursive fallback) | CTA buttons (primary, secondary, danger) |
| `FontBody` | Lexend 400 | Body text, grid cell letters |
| `FontUi` | Plus Jakarta Sans 500 | Menus, ghost buttons, UI labels |
| `FontLabel` | Spline Sans 400 | Small uppercase labels, metadata |
| `FontGrid` | Lexend 400 | Grid cell letters (separate token for grid-specific overrides) |

### Colors (`CocroColors`)

See `docs/design/option-2.md` for the full palette. Key tokens:

```kotlin
object CocroColors {
    val paper        = Color(0xFFF5F0E8)   // background
    val paperDark    = Color(0xFFEDE8D0)   // card backgrounds
    val surface      = Color(0xFFFFFFFF)
    val surfaceAlt   = Color(0xFFF7F4ED)
    val forest       = Color(0xFF2D5A3D)   // primary accent
    val forestDark   = Color(0xFF1E3E2A)   // button shadow
    val ink          = Color(0xFF1A1A1A)
    val border       = Color(0xFF1A1A1A)
    val borderSoft   = Color(0xFFC8C0A8)   // Fusion B (was #D4CEC4)
    val marginLine   = Color(0xB3E8A090)   // Séyès margin line
    val red          = Color(0xFFB83225)
    val gold         = Color(0xFFC4952A)
}
```

### Participant Cursor Colors

```kotlin
val CURSOR_COLORS = listOf(
    Color(0xFF2D5A3D),  // Slot 0 — local player (forest green)
    Color(0xFF2C4A8A),  // Slot 1 — ink blue
    Color(0xFFB83225),  // Slot 2 — red
    Color(0xFFC4952A),  // Slot 3 — gold
)
```

Local player: colored border (2dp) + light background tint on selected cell.
Other participants: 9dp colored square in bottom-right corner of their cursor cell.

---

## Module Structure

```
cocro-cmp/
├── composeApp/
│   ├── src/
│   │   ├── commonMain/kotlin/com/cocro/
│   │   │   ├── App.kt                         — root composable, screen switch
│   │   │   ├── AppViewModel.kt                — single ViewModel, all intents
│   │   │   ├── Screen.kt                      — sealed Screen interface
│   │   │   ├── network/
│   │   │   │   ├── CocroHttpClient.kt
│   │   │   │   ├── AuthApiClient.kt
│   │   │   │   ├── SessionApiClient.kt
│   │   │   │   ├── StompClient.kt
│   │   │   │   └── SessionWebSocketClient.kt
│   │   │   ├── ui/
│   │   │   │   ├── theme/
│   │   │   │   │   ├── CocroColors.kt
│   │   │   │   │   └── CocroTypography.kt
│   │   │   │   ├── components/
│   │   │   │   │   ├── CocroButton.kt
│   │   │   │   │   ├── CocroTextField.kt
│   │   │   │   │   ├── CocroSeparator.kt
│   │   │   │   │   └── CocroKeyboard.kt
│   │   │   │   └── screens/
│   │   │   │       ├── LandingScreen.kt
│   │   │   │       ├── LoginScreen.kt
│   │   │   │       ├── HomeScreen.kt
│   │   │   │       ├── LobbyCreateScreen.kt
│   │   │   │       ├── LobbyRoomScreen.kt
│   │   │   │       └── GameBoardScreen.kt
│   │   │   └── model/
│   │   │       ├── SessionUiState.kt
│   │   │       └── Participant.kt
│   │   ├── androidMain/                       — Android entry point
│   │   └── iosMain/                           — iOS entry point
```
