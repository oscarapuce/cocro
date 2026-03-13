# Security Reference

## JWT

**Library**: JJWT (`io.jsonwebtoken`)
**Algorithm**: HS256 (HMAC-SHA256)
**Header**: `JwsHeader.with(MacAlgorithm.HS256).build()`

### Claims

| Claim | Type | Description |
|-------|------|-------------|
| `sub` | String | userId (UUID) |
| `username` | String | display name |
| `roles` | List<String> | e.g. `["PLAYER"]`, `["ANONYMOUS"]`, `["ADMIN"]` |
| `iat` | Long | issued-at timestamp |
| `exp` | Long | expiry timestamp |

### Token Types

| Role | Issued by | Expiry | Capabilities |
|------|-----------|--------|-------------|
| `PLAYER` | `POST /auth/login` or `POST /auth/register` | Long (configurable) | Everything except ADMIN endpoints |
| `ADMIN` | Manual issuance | Long | Everything |
| `ANONYMOUS` | `POST /auth/guest` | 4 hours | Join/leave sessions, play, view state, check grid |

### Token Issuance

`JwtTokenIssuer` builds tokens. Key critical detail: use `JwsHeader.with(MacAlgorithm.HS256).build()` — not the deprecated `Jwts.builder().setHeaderParam(...)` style.

```kotlin
fun issueToken(user: AuthenticatedUser): String = Jwts.builder()
    .header().add(JwsHeader.with(MacAlgorithm.HS256).build()).and()
    .subject(user.userId.value.toString())
    .claim("username", user.username)
    .claim("roles", user.roles.map { it.name })
    .issuedAt(Date())
    .expiration(Date(System.currentTimeMillis() + expiryMs))
    .signWith(secretKey)
    .compact()
```

---

## REST Authentication

Spring Security JWT resource server. `JwtAuthenticationConverter` reads the `roles` claim and converts entries to `GrantedAuthority` with prefix `ROLE_`.

`PrincipalCurrentUserProvider` implements `CurrentUserProvider` and reads the authenticated principal from `SecurityContextHolder`.

### SecurityConfig Rules

```
/auth/**          → permitAll (login, register, guest)
/ws/**            → permitAll (WebSocket HTTP upgrade — auth at STOMP level)
/api/**           → authenticated; role enforcement per endpoint via @PreAuthorize
```

### @PreAuthorize per Endpoint

| Endpoint | Required role(s) |
|----------|-----------------|
| `POST /api/sessions` | PLAYER, ADMIN |
| `POST /api/sessions/join` | PLAYER, ADMIN, ANONYMOUS |
| `POST /api/sessions/leave` | PLAYER, ADMIN, ANONYMOUS |
| `POST /api/sessions/start` | PLAYER, ADMIN |
| `GET /api/sessions/{code}/state` | PLAYER, ADMIN, ANONYMOUS |
| `POST /api/sessions/{code}/check` | PLAYER, ADMIN, ANONYMOUS |
| `POST /api/grids` | PLAYER, ADMIN |
| `PATCH /api/grids` | PLAYER, ADMIN |

---

## WebSocket / STOMP Authentication

The WebSocket HTTP upgrade endpoint (`/ws/**`) is `permitAll` — no HTTP-level auth is required.

Authentication happens at the **STOMP protocol level** through `StompAuthChannelInterceptor`, which implements `ExecutorChannelInterceptor`.

### Step 1 — CONNECT Frame (`preSend`, Tomcat thread)

`StompAuthChannelInterceptor.preSend()` intercepts the STOMP CONNECT frame:

1. Reads `Authorization: Bearer <token>` from STOMP headers
2. Validates JWT via `TokenAuthenticationService`
3. Builds `CocroAuthentication` from JWT claims
4. Stores it in WebSocket session attributes under two keys:
   - `SESSION_AUTH_KEY` (`"COCRO_AUTH"`) — used by `beforeHandle` to restore SecurityContextHolder
   - `SimpMessageHeaderAccessor.USER_HEADER` (`"simpUser"`) — used by `DefaultSimpUserRegistry` for private message routing (`/user/queue/...`)
5. Throws `StompAuthenticationException` on invalid/missing token → Spring converts to STOMP ERROR frame

### Step 2 — SEND/SUBSCRIBE Frames (`beforeHandle`, executor thread)

`StompAuthChannelInterceptor.beforeHandle()` runs on the `clientInboundChannel` executor thread before each message handler invocation:

1. Reads `CocroAuthentication` from `sessionAttributes[SESSION_AUTH_KEY]`
2. Sets it on `SecurityContextHolder` for the current thread
3. After handler returns, `afterMessageHandled()` clears `SecurityContextHolder`

**Why `ExecutorChannelInterceptor` (not just `ChannelInterceptor`)**:
- `preSend()` runs on the **Tomcat thread** that accepted the WebSocket frame
- STOMP message handlers run on the **`clientInboundChannel` executor thread** — a different thread with its own `SecurityContextHolder` (thread-local)
- Using only `ChannelInterceptor.preSend()` sets the context on the wrong thread
- `ExecutorChannelInterceptor.beforeHandle()` is called on the correct executor thread, immediately before the message handler runs

### Step 3 — Disconnect (`StompSessionEventListener`)

On `SessionDisconnectEvent`:
1. Clears `SecurityContextHolder`
2. Calls `heartbeatTracker.markAway(sessionId, userId)` with timestamp
3. The `HeartbeatTimeoutScheduler` handles deferred `LeaveSessionUseCase` if user does not reconnect

### Reading Auth in WebSocket Controllers

In `@MessageMapping` methods, do NOT rely on `principal: Principal?` parameter — Spring resolves nullable `Principal?` as `Optional<Principal>`, causing a type mismatch at runtime. Instead:

```kotlin
@MessageMapping("/session/{shareCode}/command")
fun handleCommand(
    @DestinationVariable shareCode: String,
    @Payload dto: UpdateGridDto,
    headerAccessor: SimpMessageHeaderAccessor,
) {
    val auth = headerAccessor.sessionAttributes
        ?.get(SESSION_AUTH_KEY) as? CocroAuthentication
        ?: throw StompAuthenticationException("Not authenticated")
    // ...
}
```

### `@SubscribeMapping` Welcome Pattern

`SessionConnectedEvent` fires **before** the client receives the CONNECTED frame, so subscribing in the connected callback causes a race condition. The fix:

1. Client subscribes to `/app/session/{shareCode}/welcome` in its `afterConnected` callback
2. Server handles via `@SubscribeMapping("/session/{shareCode}/welcome")` in `SessionWebSocketController`
3. Spring returns the `SessionWelcome` response **synchronously** to that specific subscriber

No broadcast needed — `@SubscribeMapping` with `@SendToUser` (or return value) delivers only to the subscribing session.

---

## Anonymous Auth

`POST /auth/guest` is a public endpoint (no JWT required):

```
POST /auth/guest
Content-Type: application/json
{}
→ 200 AuthSuccess { token, userId, username, roles: ["ANONYMOUS"] }
```

### GuestLoginUseCase Flow

1. Generates a random spice name: `"Cardamome-4821"` (word from wordlist + 4-digit number)
2. Creates an in-memory `UserId` (random UUID) — **not persisted to MongoDB**
3. Issues JWT with role `ANONYMOUS`, expiry 4 hours
4. Returns `AuthSuccess` with the same shape as regular login

Anonymous users are fully stateless from the server's perspective: no `UserDocument` is created. The userId in the JWT is the only identity anchor. If the token expires, the user must call `/auth/guest` again (getting a new userId and username).

### CocroAuthentication

`CocroAuthentication` implements Spring Security's `Authentication`. It wraps `AuthenticatedUser` (userId, username, roles) and is used as the `Principal` throughout the BFF.

```kotlin
class CocroAuthentication(val user: AuthenticatedUser) : AbstractAuthenticationToken(
    user.roles.map { SimpleGrantedAuthority("ROLE_${it.name}") }
) {
    override fun getCredentials() = null
    override fun getPrincipal() = user
    init { isAuthenticated = true }
}
```
