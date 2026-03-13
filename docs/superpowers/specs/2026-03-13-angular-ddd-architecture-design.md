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
| Grid editor | Hors scope — feature future non DDD-ifiée pour l'instant |

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
│   ├── auth/
│   │   ├── login.usecase.ts
│   │   ├── register.usecase.ts
│   │   └── guest-login.usecase.ts
│   └── session/
│       ├── create-session.usecase.ts
│       ├── join-session.usecase.ts
│       ├── leave-session.usecase.ts
│       ├── start-session.usecase.ts
│       ├── place-letter.usecase.ts
│       ├── clear-cell.usecase.ts
│       └── observe-session-events.usecase.ts
│
├── infrastructure/
│   ├── auth/
│   │   └── auth-repository.impl.ts
│   ├── session/
│   │   └── session-repository.impl.ts
│   ├── store/
│   │   └── local-storage-token.store.ts
│   └── mapper/
│       ├── auth.mapper.ts
│       └── session.mapper.ts
│
└── ui/
    ├── shared/
    │   └── components/            ← button, card, input (déplacés, inchangés)
    ├── landing/
    ├── auth/
    ├── home/
    ├── lobby/
    ├── game/
    └── grid-editor/               ← non DDD-ifié (hors scope)
```

**Fichiers conservés inchangés (déplacés seulement) :**
- `StompService` → `infrastructure/session/stomp.service.ts`
- `GridService` → `infrastructure/grid/grid.service.ts`
- Guards (`authGuard`, `playerGuard`) → `ui/shared/guards/`
- Intercepteur JWT → `ui/shared/interceptors/`
- Routes → inchangées

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
// NB: pas de liste participants — construite dans le composant depuis les events WS

// client-participant.model.ts
export interface ClientParticipant {
  userId: string
  username: string
  isOnline: boolean
}

// session-event.model.ts — union discriminée (reprend l'existant, déplacé ici)
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
  topicToSubscribe: string
  participantCount: number
  status: SessionStatus
  gridRevision: number
}
// ... autres event interfaces
```

### domain/error/

```typescript
// auth.error.ts
export type AuthError = 'INVALID_CREDENTIALS' | 'USERNAME_TAKEN' | 'SERVER_ERROR'

// session.error.ts
export type SessionError = 'NOT_FOUND' | 'FULL' | 'ALREADY_JOINED' | 'SERVER_ERROR'
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
  abstract getState(shareCode: string): Observable<ClientSession>
  abstract placeLetter(shareCode: string, x: number, y: number, letter: string): void
  abstract clearCell(shareCode: string, x: number, y: number): void
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

Type résultat partagé :

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
// RegisterUseCase, GuestLoginUseCase — même pattern
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

// place-letter.usecase.ts — synchrone, pas de Result
@Injectable({ providedIn: 'root' })
export class PlaceLetterUseCase {
  private session = inject(SessionRepository)
  execute(shareCode: string, x: number, y: number, letter: string): void {
    this.session.placeLetter(shareCode, x, y, letter)
  }
}

// observe-session-events.usecase.ts
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
      .pipe(map(r => SessionMapper.toDomain(shareCode, r)))
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
  toDomain: (shareCode: string, dto: SessionJoinResponse): ClientSession => ({
    sessionId: dto.sessionId,
    shareCode,
    status: 'CREATING',
    participantCount: dto.participantCount,
  })
}
```

### Wiring — app.config.ts

```typescript
export const appConfig: ApplicationConfig = {
  providers: [
    { provide: AuthRepository,    useClass: AuthRepositoryImpl },
    { provide: SessionRepository, useClass: SessionRepositoryImpl },
    { provide: TokenStore,        useClass: LocalStorageTokenStore },
    provideHttpClient(withInterceptors([jwtInterceptor])),
    provideRouter(routes),
  ]
}
```

---

## Couche UI

Les composants smart injectent les use cases et gèrent l'état signal local. Ils jouent le rôle des ViewModels CMP.

### LandingComponent

```typescript
@Component({ ... })
export class LandingComponent {
  private guestLogin  = inject(GuestLoginUseCase)
  private joinSession = inject(JoinSessionUseCase)
  private tokenStore  = inject(TokenStore)
  private router      = inject(Router)

  shareCode = signal('')
  loading   = signal(false)
  error     = signal<SessionError | null>(null)

  isAuthenticated = computed(() => this.tokenStore.getToken() !== null)

  join(): void {
    this.loading.set(true)
    const code = this.shareCode()
    const doJoin$ = () => this.joinSession.execute(code)

    const source$ = this.isAuthenticated()
      ? doJoin$()
      : this.guestLogin.execute().pipe(switchMap(() => doJoin$()))

    source$.subscribe(result => {
      if (result.ok) this.router.navigate(['/lobby/room', code])
      else { this.error.set(result.error); this.loading.set(false) }
    })
  }
}
```

### GameBoardComponent

```typescript
@Component({ ... })
export class GameBoardComponent implements OnInit, OnDestroy {
  private observeEvents = inject(ObserveSessionEventsUseCase)
  private placeLetter   = inject(PlaceLetterUseCase)
  private clearCell     = inject(ClearCellUseCase)
  private sessionRepo   = inject(SessionRepository)
  private tokenStore    = inject(TokenStore)
  private sessionSvc    = inject(SessionRepository)
  private router        = inject(Router)
  private route         = inject(ActivatedRoute)

  shareCode    = signal('')
  participants = signal<ClientParticipant[]>([])
  cellMap      = signal(new Map<string, GridCell>())
  revision     = signal(0)

  private sub?: Subscription

  ngOnInit(): void {
    const code = this.route.snapshot.paramMap.get('shareCode')!
    this.shareCode.set(code)
    this.sessionRepo.connect(code, this.tokenStore.getToken()!)
    this.sub = this.observeEvents.execute().subscribe(e => this.handleEvent(e))
  }

  ngOnDestroy(): void {
    this.sessionRepo.disconnect()
    this.sub?.unsubscribe()
  }

  onKey(x: number, y: number, letter: string): void {
    this.placeLetter.execute(this.shareCode(), x, y, letter)
    this.applyLocalCell(x, y, letter)  // optimistic update
  }
}
```

---

## Migration — table de correspondance

| Existant | Nouveau |
|---|---|
| `shared/models/auth.models.ts` | `domain/model/auth-user.model.ts` |
| `shared/models/session.models.ts` | `domain/model/client-session.model.ts`, `session-status.model.ts` |
| `shared/models/session-events.models.ts` | `domain/model/session-event.model.ts` |
| `shared/models/grid.models.ts` | `domain/model/grid-cell.model.ts`, `cell-type.model.ts` |
| `shared/services/auth.service.ts` | `AuthRepositoryImpl` + use cases auth + `LocalStorageTokenStore` |
| `shared/services/session.service.ts` | `SessionRepositoryImpl` (partie REST) |
| `shared/services/stomp.service.ts` | `infrastructure/session/stomp.service.ts` (inchangé) |
| `shared/services/grid.service.ts` | `infrastructure/grid/grid.service.ts` (inchangé) |
| `shared/components/` | `ui/shared/components/` (inchangés) |
| `shared/guards/` | `ui/shared/guards/` (inchangés) |
| `shared/interceptors/` | `ui/shared/interceptors/` (inchangé) |
| Logique métier dans composants | extraite dans use cases |

---

## Ce qui ne change pas

- `StompService` — API inchangée, déplacé seulement
- `GridService` — API inchangée, déplacé seulement
- Tous les guards
- L'intercepteur JWT
- Les routes (`app.routes.ts`)
- Les templates HTML des composants
- Le design system (button, card, input) — déplacé, inchangé
- `grid-editor/` — hors scope DDD, fonctionnel tel quel
