# Session — DDD Documentation

This document explains how collaborative crossword sessions work end-to-end,
following the layered DDD/Clean Architecture of the project.

---

## Table of Contents

1. [Domain Model](#1-domain-model)
2. [Session Lifecycle](#2-session-lifecycle)
3. [Grid State Management](#3-grid-state-management)
4. [Participants](#4-participants)
5. [Error Model](#5-error-model)
6. [Application Layer — Use Cases](#6-application-layer--use-cases)
7. [Real-Time Layer — WebSocket](#7-real-time-layer--websocket)
8. [Infrastructure](#8-infrastructure)
9. [Background Schedulers](#9-background-schedulers)

---

## 1. Domain Model

All domain types live in `cocro-shared` — no framework dependencies.

### Session (Aggregate Root)

```
Session
├── id: SessionId
├── shareCode: SessionShareCode        ← URL-safe join code
├── gridId: GridId                     ← which crossword grid is played
├── status: SessionStatus
├── participants: List<Participant>
├── sessionGridState: SessionGridState ← current grid content + revision
└── createdAt: Instant
```

A `Session` is the central aggregate. All mutations go through
`Session.apply(SessionLifecycleCommand)` which returns a new `Session`
(immutable style) or a domain error.

### SessionStatus (state machine)

```
CREATING  ──(start)──►  PLAYING  ──(all leave)──►  INTERRUPTED
                            │
                         (score)
                            │
                            ▼
                         SCORING  ──►  ENDED
```

| Status       | Description                                  |
|--------------|----------------------------------------------|
| CREATING     | Session created, waiting for players to join |
| PLAYING      | Session started by creator                   |
| SCORING      | Scoring phase (future)                       |
| ENDED        | Completed normally                           |
| INTERRUPTED  | All active participants left                 |

### SessionShareCode

A short alphanumeric code (validated by `SessionShareCodeRule`).
Used as the primary join key and as the routing key for WebSocket topics.

---

## 2. Session Lifecycle

### Create

1. Creator calls `POST /api/sessions { gridId }`.
2. `CreateSessionUseCase` validates the `gridId` format, generates a unique `SessionShareCode`, creates the `Session` with status `CREATING` and the creator as first participant (`InviteStatus.JOINED`).
3. Session is persisted to MongoDB.
4. Response: `{ shareCode }`.

### Join

1. Player calls `POST /api/sessions/join { shareCode }`.
2. `JoinSessionUseCase` loads the session, applies `SessionLifecycleCommand.Join(userId)`.
3. Domain rules:
   - Session must be in `CREATING` status.
   - Participant must not already be `JOINED` or `LEFT`.
   - Active participants (`JOINED`) must be < `ParticipantsRule.MAX_ACTIVE_PARTICIPANTS` (= **4**).
4. On success: participant added with `InviteStatus.JOINED`, `ParticipantJoined` event broadcast.
5. Errors: `SessionFull` (→ 409), `AlreadyParticipant` (→ 409), `InvalidStatusForAction` (→ 400).

### Start

1. Creator calls `POST /api/sessions/start { shareCode }`.
2. `StartSessionUseCase` applies `SessionLifecycleCommand.Start(userId)`.
3. Domain rules:
   - Only the creator (`Participant.isCreator = true`) can start.
   - Session must be in `CREATING` status.
4. On success: `Session.status = PLAYING`, `SessionStarted` event broadcast.

### Leave

1. Player calls `POST /api/sessions/leave { shareCode }`.
2. `LeaveSessionUseCase` applies `SessionLifecycleCommand.Leave(userId)`.
3. Participant's `InviteStatus` → `LEFT`.
4. If no active participants remain → `Session.status = INTERRUPTED`.
5. `ParticipantLeft { reason: "explicit" }` event broadcast.

---

## 3. Grid State Management

### SessionGridState

```
SessionGridState
├── revision: SessionGridRevision   ← monotonically increasing (Long)
└── cells: Map<Position, SessionGridCellState>
```

`SessionGridCellState` is a sealed type:
- `Letter(char: Char)` — a letter has been placed
- `Empty` — the cell is clear

### SessionGridCommand

```
sealed class SessionGridCommand
├── SetLetter(posX, posY, letter: Char)
└── ClearCell(posX, posY)
```

`SessionGridState.apply(command)` returns a new `SessionGridState`
with `revision + 1`. It never mutates in place.

### Update Flow (WebSocket only)

Grid updates are **WebSocket-only** — there is no REST endpoint for grid updates.

`SEND /app/session/{shareCode}/grid { posX, posY, commandType, letter? }`

1. `UpdateSessionGridUseCase` loads the session from MongoDB and the cached state from Redis.
2. Applies the `SessionGridCommand`.
3. Writes the new state back to Redis using CAS (compare-and-swap on revision).
4. On success: broadcasts `GridUpdated` to `/topic/session/{shareCode}`.
5. On CAS conflict (stale revision): sends private `SyncRequired { currentRevision }` to the sender.

### Resync

If a client receives `SyncRequired`, it calls:

`GET /api/sessions/{shareCode}/state`

`GetSessionStateUseCase` reads from Redis (or falls back to MongoDB)
and returns `{ revision, cells[] }`.

---

## 4. Participants

```
Participant
├── userId: UserId
├── isCreator: Boolean
└── inviteStatus: InviteStatus
```

### InviteStatus

| Status  | Meaning                               |
|---------|---------------------------------------|
| INVITED | Placeholder (future invite flow)      |
| JOINED  | Active in the session                 |
| LEFT    | Explicitly left or timed out          |
| KICKED  | Removed by creator (future)           |

`ParticipantsRule.countActiveParticipants(participants)` counts only `JOINED`.
`MAX_ACTIVE_PARTICIPANTS = 4`.

---

## 5. Error Model

Business operations return `CocroResult<T, E>` (defined in `cocro-shared`):

```kotlin
sealed class CocroResult<out T, out E> {
    data class Success<T>(val value: T) : CocroResult<T, Nothing>()
    data class Error<E>(val errors: List<E>) : CocroResult<Nothing, E>()
}
```

### Session Errors

| Error                  | HTTP | Meaning                                    |
|------------------------|------|--------------------------------------------|
| SessionNotFound        | 404  | No session with given shareCode            |
| InvalidStatusForAction | 400  | Action not allowed in current status       |
| NotCreator             | 403  | Only creator can perform this action       |
| AlreadyParticipant     | 409  | User already joined (or left) this session |
| SessionFull            | 409  | MAX_ACTIVE_PARTICIPANTS reached            |
| InvalidShareCode       | 400  | Share code fails format validation         |
| InvalidGridId          | 400  | Grid ID not found                          |
| GridCellOutOfBounds    | 400  | (posX, posY) outside grid dimensions      |
| InvalidLetter          | 400  | Letter value not allowed                   |

`ErrorMapper` in the presentation layer converts these to RFC 7807 problem responses.

---

## 6. Application Layer — Use Cases

All use cases are in `cocro-bff/application/session/usecase/`.
They have no Spring dependencies — only ports (interfaces).

| Use Case                  | Port dependencies                                           |
|---------------------------|-------------------------------------------------------------|
| `CreateSessionUseCase`    | `SessionRepository`, `CurrentUserProvider`                  |
| `JoinSessionUseCase`      | `SessionRepository`, `CurrentUserProvider`, `SessionNotifier` |
| `LeaveSessionUseCase`     | `SessionRepository`, `CurrentUserProvider`, `SessionNotifier` |
| `StartSessionUseCase`     | `SessionRepository`, `CurrentUserProvider`, `SessionNotifier` |
| `UpdateSessionGridUseCase`| `SessionRepository`, `SessionGridStateCache`, `SessionNotifier`, `CurrentUserProvider` |
| `GetSessionStateUseCase`  | `SessionRepository`, `SessionGridStateCache`, `CurrentUserProvider` |

### Key Ports

| Port                    | Description                                         |
|-------------------------|-----------------------------------------------------|
| `SessionRepository`     | Persist/load Session from MongoDB                   |
| `SessionGridStateCache` | Redis CAS cache for SessionGridState                |
| `SessionNotifier`       | Broadcast SessionEvent via STOMP                    |
| `CurrentUserProvider`   | Read authenticated user from SecurityContextHolder  |
| `HeartbeatTracker`      | Track last-seen timestamps in Redis                 |

---

## 7. Real-Time Layer — WebSocket

### Connection

- Endpoint: `ws://<host>/ws` (STOMP over WebSocket)
- Auth: STOMP CONNECT frame with `Authorization: Bearer <token>` header
- `StompAuthChannelInterceptor` (implements `ExecutorChannelInterceptor`):
  - **CONNECT** (`preSend`): validates JWT, stores `CocroAuthentication` and `shareCode` in WebSocket session attributes.
  - **SEND/SUBSCRIBE** (`beforeHandle`): restores auth to `SecurityContextHolder` on the executor thread (thread-local — different from Tomcat thread where `preSend` runs).
  - **After handling** (`afterMessageHandled`): clears `SecurityContextHolder`.

### Welcome

After CONNECT, the client immediately subscribes to:

```
SUBSCRIBE /app/session/{shareCode}/welcome
```

`SessionWebSocketController.onWelcomeSubscribe()` (annotated `@SubscribeMapping`) runs synchronously and returns `SessionWelcome`:

```json
{
  "type": "SessionWelcome",
  "shareCode": "ABC123",
  "topicToSubscribe": "/topic/session/ABC123",
  "participantCount": 2,
  "status": "CREATING",
  "gridRevision": 0
}
```

The synchronous `@SubscribeMapping` pattern avoids the timing race
that would occur with a `SessionConnectedEvent` approach
(server fires the event before the client finishes subscribing).

### Ongoing Events

The client then subscribes to `/topic/session/{shareCode}` for broadcasts.

| Event             | Direction  | Destination                  | Trigger                        |
|-------------------|------------|------------------------------|--------------------------------|
| `ParticipantJoined` | broadcast | `/topic/session/{shareCode}` | successful join                |
| `ParticipantLeft`   | broadcast | `/topic/session/{shareCode}` | leave or heartbeat timeout     |
| `SessionStarted`    | broadcast | `/topic/session/{shareCode}` | creator calls start            |
| `GridUpdated`       | broadcast | `/topic/session/{shareCode}` | successful grid update         |
| `SyncRequired`      | private   | `/user/queue/session`        | CAS conflict on grid update    |

`SyncRequired` is sent privately via `SimpMessagingTemplate.convertAndSendToUser()`
using the `DefaultSimpUserRegistry`, which resolves user → session via the
`simpUser` session attribute set during CONNECT.

### Jackson Serialization

`SessionEvent` is a sealed Kotlin interface. Each subtype carries a `"type"` discriminator field:

```kotlin
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(SessionEvent.SessionWelcome::class, name = "SessionWelcome"),
    // ...
)
sealed interface SessionEvent
```

---

## 8. Infrastructure

### MongoDB

- Primary persistence for `Session`, `Grid`, `User`.
- Documents in `cocro-bff/infrastructure/persistence/mongo/`.
- Spring Data MongoDB repositories.

### Redis

Two responsibilities:

1. **Session grid state cache** (`RedisSessionGridStateCache`):
   - Key: `session:grid:{sessionId}`
   - Value: serialized `SessionGridState`
   - CAS via Lua script on `revision` field
   - Flushed to MongoDB periodically by `SessionFlushScheduler`

2. **Heartbeat tracker** (`RedisHeartbeatTracker`):
   - Key: `heartbeat:{sessionId}:{userId}`
   - Value: last-seen timestamp
   - TTL auto-expires after grace period
   - Read by `HeartbeatTimeoutScheduler`

### JWT

- JJWT library (`JwtTokenIssuer`, `JwtTokenAuthenticator`)
- Claims: `userId`, `username`, `roles`
- Used by both HTTP filter (`JwtAuthenticationFilter`) and STOMP interceptor

---

## 9. Background Schedulers

Both schedulers run inside the BFF application context (Spring `@Scheduled`).

### HeartbeatTimeoutScheduler

- Runs every **15 seconds**
- Grace period: **30 seconds**
- For each tracked `(sessionId, userId)`: if `lastHeartbeat > 30s ago`
  → calls `LeaveSessionUseCase` with `reason = "timeout"`
  → broadcasts `ParticipantLeft { reason: "timeout" }`
- Clients are expected to send a heartbeat (STOMP frame or REST ping)
  at least every 30 seconds to stay active.

### SessionFlushScheduler

- Runs every **30 seconds**
- Scans Redis for session grid states that have been modified since last flush
- Writes updated `SessionGridState` to MongoDB
- Ensures durability without blocking the hot update path

---

## Summary — Happy Path (Creator + 1 Player)

```
Creator                              Player
  |                                    |
  |-- POST /api/sessions -----------> |
  |<- 201 { shareCode }               |
  |                                    |
  |                                    |-- POST /api/sessions/join -->
  |                                    |<- 200 { participantCount: 2 }
  |
  |-- POST /api/sessions/start -----> (broadcasts SessionStarted)
  |
  |-- WS CONNECT (JWT + shareCode)    |-- WS CONNECT (JWT + shareCode)
  |-- SUBSCRIBE /app/.../welcome      |-- SUBSCRIBE /app/.../welcome
  |<- SessionWelcome                  |<- SessionWelcome
  |-- SUBSCRIBE /topic/session/...    |-- SUBSCRIBE /topic/session/...
  |
  |-- SEND /app/.../grid -----------> (broadcasts GridUpdated)
  |<- GridUpdated                    |<- GridUpdated
  |
  |-- GET /api/sessions/.../state --> (resync after SyncRequired)
  |<- { revision, cells[] }
```
