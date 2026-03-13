# Angular Frontend Reference

## Stack

- Angular 20, standalone components, Signals
- `@stomp/stompjs` + `sockjs-client` for WebSocket (CJS, allowlisted in `angular.json` `allowedCommonJsDependencies`)
- JWT stored in `localStorage` under keys `cocro_token` and `cocro_user`
- Design: "L'Atelier du Cruciverbiste — Fusion B (Cahier de Notes)" — see `docs/design/option-2.md`

## Directory Structure (`src/app/`)

```
app/
├── core/
│   ├── services/
│   │   ├── auth.service.ts       — JWT storage, login/register/guest, computed signals
│   │   └── session.service.ts    — REST session calls (create, join, leave, start, state, check)
│   ├── interceptors/
│   │   └── jwt.interceptor.ts    — adds Authorization: Bearer header to all /api requests
│   └── guards/
│       ├── auth.guard.ts         — requires PLAYER or ADMIN role
│       └── connected.guard.ts    — allows ANONYMOUS (any valid token)
├── features/
│   ├── landing/                  — public homepage (unauthenticated + anonymous)
│   ├── home/                     — dashboard (PLAYER/ADMIN only)
│   ├── auth/                     — login + register pages
│   ├── lobby/
│   │   ├── create/               — create session form
│   │   └── room/                 — waiting room (participants list, share code, start button)
│   └── game/
│       └── board/                — game board: grid + clue display + custom keyboard
└── shared/
    ├── components/               — design system components (buttons, separators, text fields)
    └── models/                   — TypeScript interfaces (AuthUser, SessionState, Participant, etc.)
```

## Auth Service Signals

```typescript
// core/services/auth.service.ts
currentUser = signal<AuthUser | null>(null)

isAnonymous  = computed(() => this.currentUser()?.roles.includes('ANONYMOUS') ?? false)
isPlayer     = computed(() => this.currentUser()?.roles.some(r => ['PLAYER','ADMIN'].includes(r)) ?? false)
isLoggedIn   = computed(() => this.currentUser() !== null)
token        = computed(() => this.currentUser()?.token ?? null)
```

`AuthUser` interface:
```typescript
interface AuthUser {
  userId: string
  username: string
  roles: string[]
  token: string
}
```

On init, `AuthService` reads `localStorage.getItem('cocro_user')` and populates `currentUser`. On login/register/guest, stores both `cocro_token` (raw JWT string) and `cocro_user` (serialized `AuthUser`).

**Important**: always use `inject()` at field level (not constructor injection) when field initialization references an injected service. Constructor-level injection can cause initialization order issues in standalone components.

## STOMP Client

`@stomp/stompjs` `Client` configured with `SockJS` transport:

```typescript
const client = new Client({
  webSocketFactory: () => new SockJS('/ws'),
  connectHeaders: { Authorization: `Bearer ${token}` },
  reconnectDelay: 5000,
})
```

### Subscribe Pattern

```
1. client.activate()
2. onConnect (afterConnected callback):
   a. SUBSCRIBE /app/session/{shareCode}/welcome   → receive SessionWelcome (synchronous reply)
   b. SUBSCRIBE /topic/session/{shareCode}         → receive broadcasts (ParticipantJoined, GridUpdated, etc.)
   c. SUBSCRIBE /user/queue/session                → receive private events (SyncRequired)
3. On SyncRequired: call GET /api/sessions/{code}/state to rehydrate grid
```

### Sending Commands

```typescript
client.publish({
  destination: `/app/session/${shareCode}/command`,
  body: JSON.stringify({ shareCode, x, y, commandType: 'PLACE_LETTER', letter: 'A' }),
})
```

### SockJS and allowedCommonJsDependencies

`sockjs-client` uses CJS modules. In `angular.json`, add to the build options:

```json
"allowedCommonJsDependencies": ["sockjs-client"]
```

---

## Route Guards

### `authGuard` (playerGuard)

Blocks navigation if the user is not authenticated as PLAYER or ADMIN. Redirects to `/`.

```typescript
export const authGuard: CanActivateFn = () => {
  const auth = inject(AuthService)
  return auth.isPlayer() ? true : inject(Router).createUrlTree(['/'])
}
```

### `connectedGuard`

Blocks navigation if there is no token at all (unauthenticated). Allows ANONYMOUS users.

```typescript
export const connectedGuard: CanActivateFn = () => {
  const auth = inject(AuthService)
  return auth.isLoggedIn() ? true : inject(Router).createUrlTree(['/'])
}
```

### Route Configuration

```typescript
{ path: 'home', component: HomeComponent, canActivate: [authGuard] },
{ path: 'lobby/create', component: LobbyCreateComponent, canActivate: [authGuard] },
{ path: 'lobby/room/:shareCode', component: LobbyRoomComponent, canActivate: [connectedGuard] },
{ path: 'game/board/:shareCode', component: GameBoardComponent, canActivate: [connectedGuard] },
{ path: 'login', component: LoginComponent },
{ path: 'register', component: RegisterComponent },
{ path: '', component: LandingComponent },
```

---

## Anonymous Flow

1. User enters a share code on the landing page
2. `authService.createGuest()` → `POST /auth/guest` → stores anonymous JWT in localStorage
3. `sessionService.join(shareCode)` → `POST /api/sessions/join`
4. Navigate to `/lobby/room/:shareCode`
5. Anonymous user participates via WebSocket normally (STOMP auth with `ANONYMOUS` JWT)

**On login while anonymous**:
1. Call `sessionService.leave(shareCode)` using the anonymous token
2. Perform `authService.login(username, password)` → replace token
3. Redirect to `/home` (or re-join if desired)

---

## Key TypeScript Interfaces

```typescript
// shared/models/session.model.ts
interface SessionState {
  sessionId: string
  shareCode: string
  status: 'CREATING' | 'PLAYING' | 'INTERRUPTED' | 'SCORING' | 'ENDED'
  participants: Participant[]
  gridRevision: number
  cells: CellStateDto[]
  isCreator: boolean
}

interface Participant {
  userId: string
  username: string
  isOnline: boolean
}

interface CellStateDto {
  x: number
  y: number
  type: 'LETTER' | 'CLUE_SINGLE' | 'CLUE_DOUBLE' | 'BLACK'
  letter?: string
  clueH?: string
  clueV?: string
  lockedBy?: string
}
```

```typescript
// shared/models/session-event.model.ts
interface SessionEvent {
  type: 'SessionWelcome' | 'ParticipantJoined' | 'ParticipantLeft'
       | 'SessionStarted' | 'GridUpdated' | 'SyncRequired'
  // payload varies by type — use discriminated union or type: string + cast
}
```
