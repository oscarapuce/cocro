# Angular Frontend Reference

## Stack

- Angular 20, standalone components, Signals
- `@stomp/stompjs` + `sockjs-client` for WebSocket (CJS, allowlisted in `angular.json` `allowedCommonJsDependencies`)
- JWT stored in `localStorage` under keys `cocro_token` and `cocro_user`
- Design: "L'Atelier du Cruciverbiste — Fusion B (Cahier de Notes)" — see `docs/design/option-2.md`

## Directory Structure (`src/app/`)

DDD 4-folder architecture with path aliases (`@domain/*`, `@application/*`, `@infrastructure/*`, `@presentation/*`).

```
app/
├── domain/                       ← pure domain (no Angular deps)
│   ├── models/
│   │   ├── auth.model.ts         — AuthUser interface
│   │   ├── grid.model.ts         — Grid, Cell, Clue types
│   │   ├── grid-template.model.ts— GridTemplateResponse, CellDto
│   │   ├── session.model.ts      — SessionStatus, SessionFullResponse, SessionCreatedResponse, GridCheckResponse, …
│   │   └── session-events.model.ts — SessionEvent union + per-event interfaces
│   ├── rules/
│   │   └── grid.rules.ts
│   └── services/
│       ├── cell-utils.service.ts
│       └── grid-utils.service.ts
│
├── application/                  ← use cases + ports (no HTTP/STOMP details)
│   ├── dto/
│   │   └── grid.dto.ts
│   ├── ports/
│   │   ├── auth/auth.port.ts
│   │   ├── editor/editor-draft.port.ts
│   │   ├── grid/grid.port.ts
│   │   ├── session/game-session.port.ts   ← REST session operations
│   │   └── session/session-socket.port.ts ← STOMP connect/send/disconnect
│   ├── service/
│   │   └── grid-selector.service.ts       ← shared grid state (selected cell, letter placement)
│   └── use-cases/
│       ├── create-grid.use-case.ts
│       ├── create-session.use-case.ts     ← orchestrates createSession → joinSession
│       ├── join-session.use-case.ts
│       ├── leave-session.use-case.ts
│       ├── submit-grid.use-case.ts
│       └── sync-session.use-case.ts
│
├── infrastructure/               ← Angular services, HTTP adapters, guards
│   ├── adapters/
│   │   ├── editor/editor-draft-local-storage.adapter.ts
│   │   ├── grid/grid-http.adapter.ts
│   │   └── session/
│   │       ├── game-session-http.adapter.ts  ← implements GameSessionPort
│   │       ├── grid-template.mapper.ts        ← GridTemplateResponse → Grid
│   │       └── session-stomp.adapter.ts       ← implements SessionSocketPort
│   ├── auth/
│   │   ├── auth.service.ts       — JWT storage, login/register/guest, computed signals
│   │   └── jwt.interceptor.ts    — adds Authorization: Bearer header to /api requests
│   ├── environment.ts / environment.prod.ts
│   ├── guards/
│   │   ├── auth.guard.ts         — requires any valid token (PLAYER, ADMIN, ANONYMOUS)
│   │   └── player.guard.ts       — requires PLAYER or ADMIN role
│   └── http/
│       ├── network-error.ts
│       └── network-error.interceptor.ts
│
└── presentation/                 ← Angular components + routes
    ├── features/
    │   ├── auth/                 — login, register
    │   ├── grid/
    │   │   ├── editor/           — GridEditorComponent, cell/clue/letter editors
    │   │   └── play/             — GridPlayerComponent + PlayHeaderComponent + PlayInfoComponent
    │   ├── landing/              — LandingComponent
    │   └── lobby/
    │       └── create/           — CreateSessionComponent
    └── shared/
        ├── components/           — ButtonComponent, CardComponent, InputComponent, ToastComponent
        ├── front-panel/
        └── grid/                 — GridComponent (shared grid wrapper), GridCellComponent
```

## Auth Service Signals

```typescript
// infrastructure/auth/auth.service.ts
currentUser = signal<AuthUser | null>(null)

isAnonymous = computed(() => this.currentUser()?.roles.includes('ANONYMOUS') ?? false)
isPlayer    = computed(() => this.currentUser()?.roles.some(r => ['PLAYER','ADMIN'].includes(r)) ?? false)
isLoggedIn  = computed(() => this.currentUser() !== null)
token       = computed(() => this.currentUser()?.token ?? null)
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

**Important**: always use `inject()` at field level (not constructor injection) when field initialization references an injected service.

## Session Use Cases

### CreateSessionUseCase

Orchestrates `createSession → joinSession` via `switchMap`. Returns `Observable<SessionFullResponse>`.

```typescript
execute(gridId: string): Observable<SessionFullResponse> {
  return this.sessionPort.createSession({ gridId }).pipe(
    switchMap((created) => this.sessionPort.joinSession({ shareCode: created.shareCode })),
  );
}
```

`POST /api/sessions` now returns `SessionCreatedResponse { sessionId, shareCode }` — the join call provides the full `SessionFullResponse` with grid template + cells.

### GameSessionPort

```typescript
interface GameSessionPort {
  createSession(dto: CreateSessionRequest): Observable<SessionCreatedResponse>;
  joinSession(dto: JoinSessionRequest): Observable<SessionFullResponse>;
  leaveSession(dto: LeaveSessionRequest): Observable<SessionLeaveResponse>;
  getState(shareCode: string): Observable<SessionStateResponse>;
  syncSession(shareCode: string): Observable<SessionFullResponse>;  // POST /{code}/sync
  checkGrid(shareCode: string): Observable<GridCheckResponse>;      // POST /{code}/check
}
```

## Session Models

```typescript
// domain/models/session.model.ts
export type SessionStatus = 'PLAYING' | 'ENDED' | 'INTERRUPTED';

export interface SessionCreatedResponse { sessionId: string; shareCode: string; }
export interface GridCheckResponse { isComplete: boolean; isCorrect: boolean; correctCount: number; totalCount: number; }
export interface SessionFullResponse {
  sessionId: string; shareCode: string; status: SessionStatus;
  participantCount: number; topicToSubscribe: string;
  gridTemplate: GridTemplateResponse; gridRevision: number; cells: CellStateDto[];
}
```

```typescript
// domain/models/session-events.model.ts
export type SessionEventType =
  | 'SessionWelcome' | 'ParticipantJoined' | 'ParticipantLeft'
  | 'GridUpdated' | 'GridChecked' | 'SyncRequired'
  | 'SessionEnded' | 'SessionInterrupted';

export interface SessionEndedEvent extends SessionEvent {
  type: 'SessionEnded'; shareCode: string; correctCount: number; totalCount: number;
}
export interface SessionInterruptedEvent extends SessionEvent {
  type: 'SessionInterrupted'; shareCode: string;
}
```

## STOMP Client

`@stomp/stompjs` `Client` configured with `SockJS` transport:

```typescript
const client = new Client({
  webSocketFactory: () => new SockJS('/ws'),
  connectHeaders: { Authorization: `Bearer ${token}`, shareCode },
  reconnectDelay: 5000,
})
```

### Subscribe Pattern

```
1. client.activate()
2. onConnect (afterConnected callback):
   a. SUBSCRIBE /app/session/{shareCode}/welcome  → receive SessionWelcome (synchronous reply)
   b. SUBSCRIBE /topic/session/{shareCode}        → receive broadcasts
   c. SUBSCRIBE /user/queue/session               → receive private events (SyncRequired)
3. On SyncRequired: call POST /api/sessions/{code}/sync to rehydrate grid
```

### Sending Grid Commands

```typescript
client.publish({
  destination: `/app/session/${shareCode}/grid`,
  body: JSON.stringify({ posX, posY, commandType: 'PLACE_LETTER', letter: 'A' }),
})
```

---

## Route Guards

### `authGuard`

Blocks navigation if there is no token at all. Allows ANONYMOUS users. Redirects to `/`.

### `playerGuard`

Blocks navigation if the user is not authenticated as PLAYER or ADMIN. Redirects to `/`.

### Route Configuration

```typescript
{ path: '',         component: LandingComponent },
{ path: 'auth',     loadChildren: () => authRoutes },
{ path: 'home',     component: HomeComponent,          canActivate: [playerGuard] },
{ path: 'lobby',    loadChildren: () => lobbyRoutes,   canActivate: [playerGuard] },
{ path: 'grid',     loadChildren: () => editorRoutes,  canActivate: [playerGuard] },
{ path: 'play',     loadChildren: () => playRoutes,    canActivate: [authGuard] },
{ path: '**',       redirectTo: '' },
```

Play route: `play/:shareCode` → `GridPlayerComponent`
Lobby route: `lobby/create` → `CreateSessionComponent`

---

## GridPlayerComponent Lifecycle

1. `ngOnInit`: reads `:shareCode` from route, calls `joinSession` → initializes grid + STOMP connection.
2. STOMP `onConnect`: subscribes to welcome + topic.
3. Key events handled:
   - `SessionWelcome` → set status, participantCount, revision
   - `GridUpdated` → apply letter/clear from other participant
   - `GridChecked` → set `checkResult` signal
   - `SessionEnded` → set `status('ENDED')`
   - `SessionInterrupted` → set `status('INTERRUPTED')`
   - `SyncRequired` → call `syncSession` to rehydrate
4. `checkGrid()` method: calls `POST /check`, sets `checkResult` signal locally.
5. `leave()`: calls `leaveSession` + disconnect STOMP + navigate to `/`.
