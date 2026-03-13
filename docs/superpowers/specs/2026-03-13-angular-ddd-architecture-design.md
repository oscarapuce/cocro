# Spec — Rearchitecture DDD de cocro-angular

**Date:** 2026-03-13
**Statut:** Approuvé
**Scope:** `cocro-angular` (Angular 20, standalone components, Signals)

---

## Contexte

L'architecture actuelle de `cocro-angular` est organisée par type (`shared/services/`, `shared/models/`, `features/`) avec des services God-Object (`AuthService`, `SessionService`) qui mélangent HTTP, persistance token, et logique métier. L'objectif est de rearchitecter en DDD intermédiaire avec couches horizontales, en miroir exact de `cocro-cmp`, pour que les deux frontends partagent le même vocabulaire architectural.

En parallèle, plusieurs écarts avec la charte design Fusion B — Cahier de Notes ont été identifiés et seront corrigés.

---

## Décisions d'architecture

| Décision | Choix |
|---|---|
| Organisation des couches | **Horizontale** — `domain/`, `application/`, `infrastructure/`, `ui/` à la racine de `src/app/` |
| Ports | **Abstract classes** (pas interfaces TS) — permet à Angular DI de les utiliser comme tokens sans `InjectionToken` boilerplate |
| Use Cases | `@Injectable({ providedIn: 'root' })` avec une méthode `execute()` |
| Résultat use case | Type discriminé `Result<T, E>` — `{ ok: true, value: T } \| { ok: false, error: E }` |
| État UI | Signals locaux dans les composants smart (miroir ViewModels CMP) |
| Wiring DI | `app.config.ts` — un seul endroit, miroir de `MainActivity` CMP |
| `HomeComponent` | DDD-ifié — `LogoutUseCase` + signals locaux (pas de God-Service) |
| `grid-editor/` | Hors scope — non DDD-ifié dans cette livraison |

---

## Corrections design Fusion B

Les écarts suivants sont corrigés dans cette même livraison :

| Fichier | Problème | Fix |
|---|---|---|
| `_tokens.scss` | `--color-margin-line: rgba(232,160,144,0.7)` | `#E8A090` |
| `styles.scss` | `body::before width: 1px` | `2px` + `opacity: 0.7` |
| `styles.scss` | Background : grain d'abord, ruling ensuite | Ruling d'abord, grain ensuite |
| `button.component.scss` | Ghost hover → `background: surface-alt` | `text-decoration: underline` |
| `button.component.scss` | Secondary hover → inversion forest/white | Lift uniquement (`translate + shadow`) |

---

## Structure des packages

```
src/app/
│
├── domain/
│   ├── model/
│   │   ├── auth-user.model.ts
│   │   ├── client-session.model.ts
│   │   ├── session-state-snapshot.model.ts
│   │   ├── client-participant.model.ts
│   │   ├── grid-cell.model.ts
│   │   ├── cell-type.model.ts
│   │   ├── session-status.model.ts
│   │   └── session-event.model.ts
│   ├── error/
│   │   ├── auth.error.ts
│   │   └── session.error.ts
│   └── port/
│       ├── auth.repository.ts
│       ├── session.repository.ts
│       └── token-store.port.ts
│
├── application/
│   ├── result.ts                          ← type Result<T, E> partagé
│   ├── auth-error.mapper.ts               ← mapAuthError, mapSessionError
│   ├── auth/
│   │   ├── login.usecase.ts
│   │   ├── register.usecase.ts
│   │   ├── guest-login.usecase.ts
│   │   └── logout.usecase.ts
│   └── session/
│       ├── create-session.usecase.ts
│       ├── join-session.usecase.ts
│       ├── leave-session.usecase.ts
│       ├── start-session.usecase.ts
│       ├── connect-session.usecase.ts
│       ├── disconnect-session.usecase.ts
│       ├── place-letter.usecase.ts
│       ├── clear-cell.usecase.ts
│       └── observe-session-events.usecase.ts
│
├── infrastructure/
│   ├── auth/
│   │   └── auth-repository.impl.ts
│   ├── session/
│   │   ├── session-repository.impl.ts
│   │   └── stomp.service.ts              ← déplacé, API inchangée
│   ├── grid/
│   │   └── grid.service.ts               ← déplacé, API inchangée
│   ├── store/
│   │   └── local-storage-token.store.ts
│   └── mapper/
│       ├── auth.mapper.ts
│       └── session.mapper.ts
│
└── ui/
    ├── shared/
    │   ├── components/                    ← button, card, input (déplacés, inchangés)
    │   ├── guards/                        ← authGuard, playerGuard (adaptés — voir §Guards)
    │   ├── interceptors/                  ← jwtInterceptor (adapté — voir §Intercepteur)
    │   └── util/
    │       └── jwt.util.ts                ← decodeRoles, decodeUsername
    ├── landing/
    ├── auth/
    ├── home/
    ├── lobby/
    ├── game/
    └── grid-editor/                       ← non DDD-ifié (hors scope)
```

---

## Couche Domaine

### domain/model/

```typescript
// auth-user.model.ts
export interface AuthUser {
  userId: string
  username: string
  roles: ('PLAYER' | 'ADMIN' | 'ANONYMOUS')[]
  token: string
}

// client-session.model.ts
export interface ClientSession {
  sessionId: string
  shareCode: string
  status: SessionStatus
  participantCount: number
}
// NB: pas de liste participants — construite dans le composant depuis les events WS.
// status est initialisé à 'CREATING' par le mapper ; la valeur réelle arrive via SessionWelcomeEvent.

// session-state-snapshot.model.ts — retourné par getState() pour la resynchronisation
export interface SessionStateSnapshot {
  sessionId: string
  shareCode: string
  revision: number
  cells: GridCell[]
}

// client-participant.model.ts
export interface ClientParticipant {
  userId: string
  username: string
  isOnline: boolean
}

// grid-cell.model.ts
export interface GridCell {
  x: number
  y: number
  letter: string
  type: CellType
}

// session-event.model.ts — union discriminée complète
export type SessionEvent =
  | SessionWelcomeEvent
  | ParticipantJoinedEvent
  | ParticipantLeftEvent
  | SessionStartedEvent
  | GridUpdatedEvent
  | SyncRequiredEvent

export interface SessionWelcomeEvent {
  type: 'SessionWelcome'
  shareCode: string
  topicToSubscribe: string   // consommé par SessionRepositoryImpl, non exposé aux composants
  participantCount: number
  status: SessionStatus
  gridRevision: number       // number (JS) — précision suffisante pour les révisions de grille attendues
}

export interface ParticipantJoinedEvent {
  type: 'ParticipantJoined'
  userId: string
  participantCount: number
}

export interface ParticipantLeftEvent {
  type: 'ParticipantLeft'
  userId: string
  participantCount: number
  reason: 'explicit' | 'timeout'
}

export interface SessionStartedEvent {
  type: 'SessionStarted'
  participantCount: number
}

export interface GridUpdatedEvent {
  type: 'GridUpdated'
  actorId: string
  posX: number
  posY: number
  commandType: 'PLACE_LETTER' | 'CLEAR_CELL'
  letter?: string
}

export interface SyncRequiredEvent {
  type: 'SyncRequired'
  currentRevision: number
}
```

### domain/error/

```typescript
// auth.error.ts
export type AuthError =
  | 'INVALID_CREDENTIALS'
  | 'USERNAME_TAKEN'
  | 'WEAK_PASSWORD'      // validation côté serveur
  | 'SERVER_ERROR'

// session.error.ts
export type SessionError =
  | 'NOT_FOUND'
  | 'FULL'
  | 'ALREADY_JOINED'
  | 'NOT_STARTED'
  | 'ALREADY_STARTED'
  | 'UNAUTHORIZED'
  | 'SERVER_ERROR'
```

### domain/port/

Ports définis comme **abstract classes** pour compatibilité Angular DI :

```typescript
// auth.repository.ts
export abstract class AuthRepository {
  abstract login(username: string, password: string): Observable<AuthUser>
  abstract register(username: string, password: string): Observable<AuthUser>
  abstract createGuest(): Observable<AuthUser>
}

// session.repository.ts
export abstract class SessionRepository {
  abstract create(gridId: string): Observable<ClientSession>
  abstract join(shareCode: string): Observable<ClientSession>
  abstract leave(shareCode: string): Observable<void>
  abstract start(shareCode: string): Observable<void>
  // Retourne un snapshot complet avec les cellules (pour la resynchronisation)
  abstract getState(shareCode: string): Observable<SessionStateSnapshot>
  // Fire-and-forget via STOMP — l'UI applique l'update localement (optimiste).
  // La confirmation arrive via GridUpdatedEvent broadcast (ou SyncRequired si désync).
  abstract placeLetter(shareCode: string, x: number, y: number, letter: string): void
  abstract clearCell(shareCode: string, x: number, y: number): void
  // connect() doit être appelé avant de souscrire à events$
  abstract connect(shareCode: string, token: string): void
  abstract disconnect(): void
  abstract readonly events$: Observable<SessionEvent>
}

// token-store.port.ts
export abstract class TokenStore {
  abstract getToken(): string | null
  abstract setToken(token: string): void
  abstract clear(): void
}
```

---

## Couche Application

```typescript
// application/result.ts
export type Result<T, E> = { ok: true; value: T } | { ok: false; error: E }
```

### Use cases Auth

```typescript
// login.usecase.ts
@Injectable({ providedIn: 'root' })
export class LoginUseCase {
  private auth  = inject(AuthRepository)
  private store = inject(TokenStore)

  execute(username: string, password: string): Observable<Result<AuthUser, AuthError>> {
    return this.auth.login(username, password).pipe(
      tap(user => this.store.setToken(user.token)),
      map(user => ({ ok: true as const, value: user })),
      catchError(err => of({ ok: false as const, error: mapAuthError(err) }))
    )
  }
}
// RegisterUseCase, GuestLoginUseCase — même pattern, stockent le token

// logout.usecase.ts
@Injectable({ providedIn: 'root' })
export class LogoutUseCase {
  private store = inject(TokenStore)

  execute(): void {
    this.store.clear()
  }
}
```

### Helpers partagés — `application/auth-error.mapper.ts`

Les mappers d'erreur HTTP → domaine sont définis une fois, importés par tous les use cases :

```typescript
export function mapAuthError(err: HttpErrorResponse): AuthError {
  if (err.status === 401) return 'INVALID_CREDENTIALS'
  if (err.status === 409) return 'USERNAME_TAKEN'
  if (err.status === 422) return 'WEAK_PASSWORD'
  return 'SERVER_ERROR'
}

export function mapSessionError(err: HttpErrorResponse): SessionError {
  if (err.status === 404) return 'NOT_FOUND'
  if (err.status === 409) {
    const code = err.error?.code as string | undefined
    if (code === 'ALREADY_STARTED') return 'ALREADY_STARTED'
    if (code === 'ALREADY_JOINED')  return 'ALREADY_JOINED'
    if (code === 'FULL')            return 'FULL'
    return 'ALREADY_JOINED'
  }
  if (err.status === 403) return 'UNAUTHORIZED'
  if (err.status === 422) return 'NOT_STARTED'
  return 'SERVER_ERROR'
}
```

### Use cases Session

```typescript
// join-session.usecase.ts
@Injectable({ providedIn: 'root' })
export class JoinSessionUseCase {
  private session = inject(SessionRepository)

  execute(shareCode: string): Observable<Result<ClientSession, SessionError>> {
    return this.session.join(shareCode).pipe(
      map(s => ({ ok: true as const, value: s })),
      catchError(err => of({ ok: false as const, error: mapSessionError(err) }))
    )
  }
}
// CreateSessionUseCase, LeaveSessionUseCase, StartSessionUseCase — même pattern

// connect-session.usecase.ts
// Doit être appelé avant ObserveSessionEventsUseCase.execute()
@Injectable({ providedIn: 'root' })
export class ConnectSessionUseCase {
  private session = inject(SessionRepository)
  private store   = inject(TokenStore)
  execute(shareCode: string): void {
    this.session.connect(shareCode, this.store.getToken()!)
  }
}

// disconnect-session.usecase.ts
@Injectable({ providedIn: 'root' })
export class DisconnectSessionUseCase {
  private session = inject(SessionRepository)
  execute(): void { this.session.disconnect() }
}

// place-letter.usecase.ts
// Fire-and-forget : STOMP send, pas de Result.
// L'UI applique l'update localement ; la confirmation arrive via GridUpdatedEvent.
@Injectable({ providedIn: 'root' })
export class PlaceLetterUseCase {
  private session = inject(SessionRepository)
  execute(shareCode: string, x: number, y: number, letter: string): void {
    this.session.placeLetter(shareCode, x, y, letter)
  }
}
// ClearCellUseCase — même pattern

// observe-session-events.usecase.ts
// Prérequis : ConnectSessionUseCase.execute() doit avoir été appelé avant
@Injectable({ providedIn: 'root' })
export class ObserveSessionEventsUseCase {
  private session = inject(SessionRepository)
  execute(): Observable<SessionEvent> { return this.session.events$ }
}
```

---

## Couche Infrastructure

### AuthRepositoryImpl

```typescript
@Injectable()
export class AuthRepositoryImpl extends AuthRepository {
  private http = inject(HttpClient)
  private base = `${environment.apiBaseUrl}/auth`

  login(username: string, password: string): Observable<AuthUser> {
    return this.http.post<AuthResponse>(`${this.base}/login`, { username, password })
      .pipe(map(AuthMapper.toDomain))
  }
  register(username: string, password: string): Observable<AuthUser> {
    return this.http.post<AuthResponse>(`${this.base}/register`, { username, password })
      .pipe(map(AuthMapper.toDomain))
  }
  createGuest(): Observable<AuthUser> {
    return this.http.post<AuthResponse>(`${this.base}/guest`, {})
      .pipe(map(AuthMapper.toDomain))
  }
}
```

### SessionRepositoryImpl

```typescript
@Injectable()
export class SessionRepositoryImpl extends SessionRepository {
  private http  = inject(HttpClient)
  private stomp = inject(StompService)
  private base  = `${environment.apiBaseUrl}/api/sessions`

  private _events$ = new Subject<SessionEvent>()
  readonly events$ = this._events$.asObservable()

  join(shareCode: string): Observable<ClientSession> {
    return this.http.post<SessionJoinResponse>(`${this.base}/join`, { shareCode })
      .pipe(map(r => SessionMapper.joinToDomain(shareCode, r)))
  }
  getState(shareCode: string): Observable<SessionStateSnapshot> {
    return this.http.get<SessionStateResponse>(`${this.base}/${shareCode}/state`)
      .pipe(map(SessionMapper.stateToDomain))
  }
  placeLetter(shareCode: string, x: number, y: number, letter: string): void {
    this.stomp.sendGridUpdate(shareCode, { posX: x, posY: y, commandType: 'PLACE_LETTER', letter })
  }
  clearCell(shareCode: string, x: number, y: number): void {
    this.stomp.sendGridUpdate(shareCode, { posX: x, posY: y, commandType: 'CLEAR_CELL' })
  }
  connect(shareCode: string, token: string): void {
    this.stomp.connect(token, shareCode, event => this._events$.next(event))
  }
  disconnect(): void { this.stomp.disconnect() }
}
```

### Mappers

```typescript
// auth.mapper.ts
export const AuthMapper = {
  toDomain: (dto: AuthResponse): AuthUser => ({
    userId: dto.userId,
    username: dto.username,
    roles: dto.roles as AuthUser['roles'],
    token: dto.token,
  })
}

// session.mapper.ts
export const SessionMapper = {
  // status initialisé à 'CREATING' — valeur provisoire, remplacée par SessionWelcomeEvent.status
  joinToDomain: (shareCode: string, dto: SessionJoinResponse): ClientSession => ({
    sessionId: dto.sessionId,
    shareCode,
    status: 'CREATING',
    participantCount: dto.participantCount,
  }),
  // `type: 'LETTER'` est intentionnel — le BFF ne retourne que les cellules avec lettre
  // dans le snapshot. Les cellules noires ne font pas partie du state endpoint (elles sont
  // fixes et portées par le GridDocument). Un CellStateDto = { x, y, letter } seulement.
  stateToDomain: (dto: SessionStateResponse): SessionStateSnapshot => ({
    sessionId: dto.sessionId,
    shareCode: dto.shareCode,
    revision: dto.revision,
    cells: dto.cells.map(c => ({ x: c.x, y: c.y, letter: c.letter, type: 'LETTER' as CellType })),
  })
}
```

### Guards et Intercepteur — adaptations requises

`authGuard`, `playerGuard` et `jwtInterceptor` injectent actuellement `AuthService` qui est supprimé. Ils sont adaptés pour injecter `TokenStore`.

**Helper JWT decode partagé** — `ui/shared/util/jwt.util.ts`

Le BFF (`JwtTokenIssuer`) émet des claims `sub` (userId), `username`, et `roles` (array). Ces helpers décodent le payload sans vérification de signature (la signature est validée côté serveur) :

```typescript
// ui/shared/util/jwt.util.ts
function decodePayload(token: string): Record<string, unknown> {
  try {
    return JSON.parse(atob(token.split('.')[1]))
  } catch {
    return {}
  }
}

export function decodeRoles(token: string): string[] {
  const payload = decodePayload(token)
  return Array.isArray(payload['roles']) ? (payload['roles'] as string[]) : []
}

export function decodeUsername(token: string | null): string {
  if (!token) return ''
  const payload = decodePayload(token)
  return typeof payload['username'] === 'string' ? payload['username'] : ''
}
```

**Guards :**

```typescript
// ui/shared/guards/auth.guard.ts
export const authGuard: CanActivateFn = () => {
  const store = inject(TokenStore)
  return store.getToken() !== null
    ? true
    : inject(Router).createUrlTree(['/auth/login'])
}

// ui/shared/guards/player.guard.ts
export const playerGuard: CanActivateFn = () => {
  const store = inject(TokenStore)
  const token = store.getToken()
  if (!token) return inject(Router).createUrlTree(['/'])
  const roles = decodeRoles(token)
  return roles.some(r => r === 'PLAYER' || r === 'ADMIN')
    ? true
    : inject(Router).createUrlTree(['/'])
}
```

**Intercepteur :**

```typescript
// ui/shared/interceptors/jwt.interceptor.ts
// Le token n'est envoyé que pour les routes /api. Les endpoints /auth (login, register, guest)
// sont publics — pas besoin de JWT. Ce comportement est intentionnel.
export const jwtInterceptor: HttpInterceptorFn = (req, next) => {
  const token = inject(TokenStore).getToken()
  if (token && req.url.includes('/api')) {
    return next(req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }))
  }
  return next(req)
}
```

### Wiring — app.config.ts

L'état final complet de `app.config.ts` (tous les providers, y compris ceux existants) :

```typescript
export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes),
    provideHttpClient(withInterceptors([jwtInterceptor])),
    { provide: AuthRepository,    useClass: AuthRepositoryImpl },
    { provide: SessionRepository, useClass: SessionRepositoryImpl },
    { provide: TokenStore,        useClass: LocalStorageTokenStore },
  ]
}
```

---

## Couche UI

Les composants smart injectent les use cases et gèrent l'état signal local. Ils jouent le rôle des ViewModels CMP. Toutes les souscriptions doivent utiliser `takeUntilDestroyed()` ou être explicitement désinscrites dans `ngOnDestroy`.

### LandingComponent

```typescript
@Component({ ... })
export class LandingComponent {
  private destroyRef   = inject(DestroyRef)
  private guestLogin   = inject(GuestLoginUseCase)
  private joinSession  = inject(JoinSessionUseCase)
  private tokenStore   = inject(TokenStore)
  private router       = inject(Router)

  shareCode       = signal('')
  loading         = signal(false)
  error           = signal<SessionError | null>(null)
  // Signal local mis à jour explicitement — getToken() n'est pas réactif
  isAuthenticated = signal(this.tokenStore.getToken() !== null)

  join(): void {
    this.loading.set(true)
    const code = this.shareCode()
    const doJoin$ = () => this.joinSession.execute(code)

    const source$ = this.isAuthenticated()
      ? doJoin$()
      : this.guestLogin.execute().pipe(
          tap(() => this.isAuthenticated.set(true)),
          switchMap(() => doJoin$())
        )

    source$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(result => {
      if (result.ok) this.router.navigate(['/lobby/room', code])
      else { this.error.set(result.error); this.loading.set(false) }
    })
  }
}
```

### HomeComponent

```typescript
@Component({ ... })
export class HomeComponent {
  private destroyRef     = inject(DestroyRef)
  private joinSessionUC  = inject(JoinSessionUseCase)
  private logout         = inject(LogoutUseCase)
  private tokenStore     = inject(TokenStore)
  private router         = inject(Router)

  // username lu depuis le token (décodage JWT local — voir jwt.util.ts)
  username    = signal(decodeUsername(this.tokenStore.getToken()))
  joinCode    = signal('')
  joinLoading = signal(false)
  joinError   = signal<SessionError | null>(null)

  doLogout(): void {
    this.logout.execute()
    this.router.navigate(['/'])
  }

  // Renommé onJoinSession pour éviter le conflit avec le champ joinSessionUC
  onJoinSession(): void {
    const code = this.joinCode()
    this.joinLoading.set(true)
    this.joinSessionUC.execute(code)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(result => {
        if (result.ok) this.router.navigate(['/lobby/room', code])
        else { this.joinError.set(result.error); this.joinLoading.set(false) }
      })
  }
}
```

### GameBoardComponent

```typescript
@Component({ ... })
export class GameBoardComponent implements OnInit, OnDestroy {
  private destroyRef      = inject(DestroyRef)
  private connectSession  = inject(ConnectSessionUseCase)
  private disconnectSession = inject(DisconnectSessionUseCase)
  private observeEvents   = inject(ObserveSessionEventsUseCase)
  private placeLetterUC   = inject(PlaceLetterUseCase)
  private clearCellUC     = inject(ClearCellUseCase)
  private router          = inject(Router)
  private route           = inject(ActivatedRoute)

  shareCode    = signal('')
  participants = signal<ClientParticipant[]>([])
  cellMap      = signal(new Map<string, GridCell>())
  revision     = signal(0)

  ngOnInit(): void {
    const code = this.route.snapshot.paramMap.get('shareCode')!
    this.shareCode.set(code)
    // ConnectSessionUseCase.execute() avant ObserveSessionEventsUseCase.execute()
    this.connectSession.execute(code)
    this.observeEvents.execute()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(e => this.handleEvent(e))
  }

  ngOnDestroy(): void {
    this.disconnectSession.execute()
  }

  onKey(x: number, y: number, letter: string): void {
    this.placeLetterUC.execute(this.shareCode(), x, y, letter)
    this.applyLocalCell(x, y, letter)  // optimistic update
  }
}
```

---

## Migration — table de correspondance

| Existant | Nouveau |
|---|---|
| `shared/models/auth.models.ts` | `domain/model/auth-user.model.ts` |
| `shared/models/session.models.ts` | `domain/model/client-session.model.ts`, `session-status.model.ts`, `session-state-snapshot.model.ts` |
| `shared/models/session-events.models.ts` | `domain/model/session-event.model.ts` |
| `shared/models/grid.models.ts` | `domain/model/grid-cell.model.ts`, `cell-type.model.ts` |
| `shared/services/auth.service.ts` | `AuthRepositoryImpl` + use cases auth + `LogoutUseCase` + `LocalStorageTokenStore` |
| `shared/services/session.service.ts` | `SessionRepositoryImpl` (HTTP) |
| `shared/services/stomp.service.ts` | `infrastructure/session/stomp.service.ts` (inchangé) |
| `shared/services/grid.service.ts` | `infrastructure/grid/grid.service.ts` (inchangé) |
| `shared/components/` | `ui/shared/components/` (inchangés) |
| `shared/guards/authGuard` | `ui/shared/guards/` — adapté pour injecter `TokenStore` |
| `shared/guards/playerGuard` | `ui/shared/guards/` — adapté pour injecter `TokenStore` |
| `shared/interceptors/jwtInterceptor` | `ui/shared/interceptors/` — adapté pour injecter `TokenStore` |
| Logique métier dans composants | extraite dans use cases |

---

### LobbyRoomComponent et CreateSessionComponent

Ces deux composants injectent actuellement `SessionService` et/ou `AuthService` qui sont supprimés. Ils sont dans le scope de cette livraison et migrent vers les use cases.

**`LobbyRoomComponent`** : injecte `JoinSessionUseCase`, `StartSessionUseCase`, `LeaveSessionUseCase`, `ObserveSessionEventsUseCase`, `ConnectSessionUseCase`, `DisconnectSessionUseCase`, `TokenStore`.

Le composant existant lit `this.auth.currentUser()?.username` (un signal produit par `AuthService`). Avec `AuthService` supprimé, ce signal est remplacé par un signal local statique :

```typescript
private tokenStore = inject(TokenStore)
// même pattern que HomeComponent — même si l'utilisateur vient de se connecter en tant
// que guest, le token est déjà stocké avant la navigation vers le lobby
currentUsername = signal(decodeUsername(this.tokenStore.getToken()))
```

`participantSlots` (qui dépend du `currentUsername` pour marquer le slot local) doit être mis à jour pour lire `this.currentUsername()` au lieu de `this.auth.currentUser()?.username`.

**`CreateSessionComponent`** : injecte `CreateSessionUseCase` uniquement. Use case :

```typescript
// create-session.usecase.ts
@Injectable({ providedIn: 'root' })
export class CreateSessionUseCase {
  private session = inject(SessionRepository)

  execute(gridId: string): Observable<Result<ClientSession, SessionError>> {
    return this.session.create(gridId).pipe(
      map(s => ({ ok: true as const, value: s })),
      catchError(err => of({ ok: false as const, error: mapSessionError(err) }))
    )
  }
}
```

On succès, le composant navigue vers `/lobby/room/${result.value.shareCode}`.

Le pattern est identique à `GameBoardComponent` — signals locaux, use cases, `takeUntilDestroyed`.

---

## Ce qui ne change pas

- `StompService` — API inchangée, déplacé seulement
- `GridService` — API inchangée, déplacé seulement
- Les routes (`app.routes.ts`)
- Les templates HTML des composants
- Le design system (button, card, input) — déplacé, inchangé
- `grid-editor/` — hors scope DDD, fonctionnel tel quel
