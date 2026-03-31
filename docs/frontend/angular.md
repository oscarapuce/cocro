# Angular Frontend Reference

## Stack

- Angular 20, standalone components, Signals
- `@stomp/stompjs` + `sockjs-client` for WebSocket (CJS, allowlisted in `angular.json` `allowedCommonJsDependencies`)
- JWT stored in `localStorage` under keys `cocro_token` and `cocro_user`
- Design: "L'Atelier du Cruciverbiste — Fusion B (Cahier de Notes)" — see `docs/design/cocro-design-charter.md`

## Directory Structure (`src/app/`)

DDD 4-folder architecture with path aliases (`@domain/*`, `@application/*`, `@infrastructure/*`, `@presentation/*`).

```
app/
├── domain/                       ← pure domain (no Angular deps)
│   ├── models/
│   │   ├── auth.model.ts         — AuthUser interface
│   │   ├── grid.model.ts         — Grid, Cell, Clue types
│   │   ├── grid-summary.model.ts — GridSummary (list view)
│   │   ├── grid-template.model.ts— GridTemplateResponse, CellDto
│   │   ├── session.model.ts      — SessionStatus, SessionFullResponse, SessionCreatedResponse, GridCheckResponse, …
│   │   ├── session-summary.model.ts — SessionSummary (list view, role: CREATOR|PARTICIPANT)
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
│   │   ├── grid-selector.service.ts       ← shared grid state (selected cell, letter placement)
│   │   └── letter-author.service.ts       ← tracks which user placed each letter
│   └── use-cases/
│       ├── check-grid.use-case.ts
│       ├── create-session.use-case.ts     ← orchestrates createSession → joinSession
│       ├── get-my-grids.use-case.ts       ← fetches user's grids (GET /api/grids/mine)
│       ├── join-session.use-case.ts
│       ├── leave-session.use-case.ts
│       ├── load-grid.use-case.ts          ← loads a single grid for editing
│       ├── save-grid.use-case.ts          ← saves/updates a grid (PATCH /api/grids)
│       ├── submit-grid.use-case.ts        ← creates a new grid (POST /api/grids)
│       └── sync-session.use-case.ts
│
├── infrastructure/               ← Angular services, HTTP adapters, guards
│   ├── adapters/
│   │   ├── editor/editor-draft-local-storage.adapter.ts
│   │   ├── grid/grid-http.adapter.ts
│   │   └── session/
│   │       ├── game-session-http.adapter.ts  ← implements GameSessionPort
│   │       ├── session-http.adapter.ts        ← session list/delete operations
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
    │   │   ├── editor/           — GridEditorComponent, cell/clue/letter/global-clue editors, grid-params
    │   │   ├── my-grids/         — MyGridsComponent + GridCardComponent (list user's grids)
    │   │   └── play/             — GridPlayerComponent + PlayHeaderComponent + PlayInfoComponent + playLeaveGuard
    │   ├── landing/              — LandingComponent (adapts to auth state: guest vs player)
    │   └── lobby/
    │       ├── create/           — CreateSessionComponent
    │       └── my-sessions/      — MySessionsComponent (list user's sessions)
    └── shared/
        ├── components/           — ButtonComponent, CardComponent, InputComponent, ToastComponent
        ├── front-panel/          — FrontPanelComponent (join/create panels on landing)
        ├── grid/                 — GridComponent (shared grid wrapper), GridCellComponent, inputs (letter, clue, arrow)
        ├── pipes/                — KebabCasePipe
        ├── shell/                — LandingHomeShellComponent (layout shell with sidebar awareness)
        ├── sidebar/              — AuthSidebarComponent (collapsible left sidebar with nav sections)
        └── user-profile/         — UserProfileWidgetComponent (displays username + role badge)
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
// domain/models/session-summary.model.ts
export interface SessionSummary {
  sessionId: string; shareCode: string;
  status: 'PLAYING' | 'INTERRUPTED' | 'ENDED';
  gridTitle: string; gridDimension: { width: number; height: number };
  authorName: string; participantCount: number;
  role: 'CREATOR' | 'PARTICIPANT';
  createdAt: string; updatedAt: string;
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

### `playLeaveGuard`

`canDeactivate` guard on play routes. Prompts user before leaving an active session (calls `leaveSession` + STOMP disconnect).

### Route Configuration

```typescript
// root.routes.ts
{ path: '',         data: { showSidebar: true }, loadComponent: () => LandingComponent },
{ path: 'auth',     loadChildren: () => authRoutes },
{ path: 'home',     redirectTo: '' },
{ path: 'grid',     data: { showSidebar: true }, canActivate: [playerGuard],  loadChildren: () => editorRoutes },
{ path: 'lobby',    data: { showSidebar: true }, canActivate: [authGuard],    loadChildren: () => lobbyRoutes },
{ path: 'play',     data: { showSidebar: true }, canActivate: [authGuard],    loadChildren: () => playRoutes },
{ path: '**',       redirectTo: '' },

// editorRoutes
{ path: 'create',         → GridEditorComponent }
{ path: ':gridId/edit',   → GridEditorComponent }
{ path: 'mine',           → MyGridsComponent }
{ path: '',               redirectTo: 'mine' }

// lobbyRoutes
{ path: 'create',  canActivate: [playerGuard],  → CreateSessionComponent }
{ path: 'mine',    canActivate: [playerGuard],  → MySessionsComponent }
{ path: '',        redirectTo: 'mine' }

// playRoutes
{ path: ':shareCode', canDeactivate: [playLeaveGuard], → GridPlayerComponent }
```

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
6. `ngOnDestroy`: calls `leaveSession` + disconnect STOMP (also triggered via `playLeaveGuard` on navigation).
7. `@HostListener('window:beforeunload')`: warns user before closing tab during active session.
