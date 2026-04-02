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

All domain types live in `cocro-bff` under the `domain` package (`com.cocro.domain.*`) — no framework dependencies.

### Session (Aggregate Root)

```
Session
├── id: SessionId
├── shareCode: SessionShareCode        ← URL-safe join code
├── author: Author                     ← { id: UserId, username: String } — who created the session
├── gridId: GridShareCode              ← which crossword grid is played
├── gridTemplate: GridTemplateSnapshot ← snapshot of the grid at session creation time
├── status: SessionStatus
├── participants: List<Participant>
├── sessionGridState: SessionGridState ← current grid content + revision
└── createdAt / updatedAt: Instant
```

> **Note:** `creatorId: UserId` was replaced by `author: Author` (a value object with `id` + `username`). A helper `val creatorId: UserId get() = author.id` exists for retro-compatibility.

A `Session` is the central aggregate. All mutations go through pure functions on `Session`
(e.g. `session.join(userId)`, `session.leave(userId)`, `session.end()`) or through
`Session.apply(SessionLifecycleCommand)` which return a new `Session` (immutable style)
or a domain error.

### SessionStatus (state machine)

```
POST /api/sessions ──► PLAYING ──[check correct]──► ENDED
                          │
                      [all leave / timeout]
                          │
                          ▼
                      INTERRUPTED
```

| Status       | Description                                                                 |
|--------------|-----------------------------------------------------------------------------|
| `PLAYING`    | Session active; grid updates via WebSocket are accepted                     |
| `INTERRUPTED`| All active participants left (explicit leave or heartbeat timeout)           |
| `ENDED`      | Grid was completed and validated as correct via `CheckGridUseCase`           |

> **Note:** `CREATING` and `SCORING` states have been removed. Sessions are created in `PLAYING` status with an empty participant list. The creator must call `POST /api/sessions/join` to enter the session.

### SessionShareCode

A short alphanumeric code (validated by `SessionShareCodeRule`).
Used as the primary join key and as the routing key for WebSocket topics.

---

## 2. Session Lifecycle

### Create

1. Creator calls `POST /api/sessions { gridId }`.
2. `CreateSessionUseCase` validates the `gridId` format, generates a unique `SessionShareCode`, creates the `Session` with `author = Author(userId, username)`, status `PLAYING`, and an **empty participant list**.
3. Session is persisted to MongoDB. Redis grid state cache is initialized.
4. Response: `SessionCreationSuccess { sessionId, shareCode }`.

The creator must then call `POST /api/sessions/join` to join their own session.

### Join

1. Player calls `POST /api/sessions/join { shareCode }`.
2. `JoinSessionUseCase` loads the session, applies `SessionLifecycleCommand.Join(userId)`.
3. Domain rules:
   - Session must be in `PLAYING` or `INTERRUPTED` status.
   - Participant must not already be `JOINED`.
   - Active participants (`JOINED`) must be < `ParticipantsRule.MAX_ACTIVE_PARTICIPANTS` (= **4**).
   - A previously `LEFT` participant may rejoin.
4. On success: participant added/updated with `ParticipantStatus.JOINED`, `ParticipantJoined` event broadcast.
5. Response: `SessionFullDto` (full session state including grid template and current cells).

### Leave

1. Player calls `POST /api/sessions/leave { shareCode }`.
2. `LeaveSessionUseCase` applies `SessionLifecycleCommand.Leave(userId)`.
3. Participant's `ParticipantStatus` → `LEFT`.
4. If no active participants remain and `status == PLAYING` → `Session.interrupt()` is called → `INTERRUPTED`.
5. Events broadcast:
   - `ParticipantLeft { userId, participantCount, reason: "explicit" }` to all
   - `SessionInterrupted { shareCode }` if session became INTERRUPTED

### Check Grid (end-of-game trigger)

1. Any participant calls `POST /api/sessions/{shareCode}/check`.
2. `CheckGridUseCase` compares current grid state against the reference grid.
3. Flushes Redis → MongoDB and broadcasts `GridChecked` to all participants.
4. If `isComplete && isCorrect`: calls `session.end()` → status `ENDED`, broadcasts `SessionEnded { shareCode, correctCount, totalCount }`.

### Sync

1. Participant calls `POST /api/sessions/{shareCode}/sync`.
2. `SynchroniseSessionUseCase` verifies caller is a JOINED participant.
3. Flushes Redis → MongoDB.
4. Returns full `SessionFullDto`.

---

## 3. Grid State Management

### SessionGridState

```
SessionGridState
├── revision: SessionGridStateRevision  ← monotonically increasing (Long)
└── cells: Map<CellPos, SessionGridCellState>
```

`SessionGridCellState` is a sealed type:
- `Letter(value: String)` — a letter has been placed

### SessionGridCommand

```
sealed class SessionGridCommand
├── PlaceLetter(posX, posY, letter: String, actorId: UserId)
└── ClearCell(posX, posY, actorId: UserId)
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
5. On CAS conflict (stale revision): catches the `IllegalStateException`, sends private `SyncRequired { currentRevision }` to the sender via `sessionNotifier.notifyUser()`, and returns `CocroResult.Error(SessionError.ConcurrentModification)`.

### Resync

If a client receives `SyncRequired` or needs to rehydrate after reconnect, it calls:

`POST /api/sessions/{shareCode}/sync`

`SynchroniseSessionUseCase` flushes the Redis state to MongoDB and returns `SessionFullDto` (full grid template + current cells + revision + participant count + status).

---

## 4. Participants

```
Participant
├── userId: UserId
├── username: String           ← display name (registered username or generated spice name for guests)
└── status: ParticipantStatus
```

### ParticipantStatus

| Status  | Meaning                                    |
|---------|--------------------------------------------|
| `JOINED`| Active in the session                      |
| `LEFT`  | Explicitly left or timed out               |

`ParticipantsRule.countActiveParticipants(participants)` counts only `JOINED`.
`MAX_ACTIVE_PARTICIPANTS = 4`.

---

## 5. Error Model

Business operations return `CocroResult<T, E>`:

```kotlin
sealed class CocroResult<out T, out E> {
    data class Success<T>(val value: T) : CocroResult<T, Nothing>()
    data class Error<E>(val errors: List<E>) : CocroResult<Nothing, E>()
}
```

### Session Errors

| Error                    | HTTP | Meaning                                    |
|--------------------------|------|--------------------------------------------|
| `SessionNotFound`        | 404  | No session with given shareCode            |
| `InvalidStatusForAction` | 400  | Action not allowed in current status       |
| `AlreadyParticipant`     | 409  | User already joined this session           |
| `SessionFull`            | 409  | MAX_ACTIVE_PARTICIPANTS reached            |
| `ConcurrentModification` | 409  | CAS conflict on grid state update          |
| `UserNotParticipant`     | 403  | User is not a joined participant           |
| `NotCreator`             | 403  | Action requires session creator            |
| `InvalidShareCode`       | 400  | Share code fails format validation         |
| `ReferenceGridNotFound`  | 404  | Reference grid not found for check         |
| `GridCellOutOfBounds`    | 400  | (posX, posY) outside grid dimensions       |
| `InvalidLetter`          | 400  | Letter value not allowed                   |
| `Unauthorized`           | 401  | User not authenticated                     |

`ErrorMapper` in the presentation layer converts these to RFC 7807 problem responses.

---

## 6. Application Layer — Use Cases

All use cases are in `cocro-bff/application/session/usecase/`.
They have no Spring dependencies — only ports (interfaces).

| Use Case                      | Port dependencies                                                                       |
|-------------------------------|-----------------------------------------------------------------------------------------|
| `CreateSessionUseCase`        | `SessionRepository`, `SessionGridStateCache`, `CurrentUserProvider`, `SessionCodeGenerator`, `GridRepository` |
| `JoinSessionUseCase`          | `SessionRepository`, `SessionGridStateCache`, `CurrentUserProvider`, `SessionNotifier`, `HeartbeatTracker` |
| `LeaveSessionUseCase`         | `SessionRepository`, `SessionGridStateCache`, `CurrentUserProvider`, `SessionNotifier`, `HeartbeatTracker` |
| `SynchroniseSessionUseCase`   | `SessionRepository`, `SessionGridStateCache`, `CurrentUserProvider`                     |
| `CheckGridUseCase`            | `SessionRepository`, `SessionGridStateCache`, `SessionNotifier`, `GridRepository`, `CurrentUserProvider`, `HeartbeatTracker` |
| `UpdateSessionGridUseCase`    | `SessionRepository`, `SessionGridStateCache`, `SessionNotifier`, `CurrentUserProvider`  |
| `GetSessionStateUseCase`      | `SessionRepository`, `SessionGridStateCache`, `CurrentUserProvider`                     |
| `DeleteSessionUseCase`        | `SessionRepository`, `SessionGridStateCache`, `HeartbeatTracker`                        |
| `GetMySessionsUseCase`        | `SessionRepository`                                                                     |

### Key Ports

| Port                    | Description                                         |
|-------------------------|-----------------------------------------------------|
| `SessionRepository`     | Persist/load Session from MongoDB                   |
| `SessionGridStateCache` | Redis CAS cache for SessionGridState                |
| `SessionNotifier`       | Broadcast SessionEvent via STOMP                    |
| `CurrentUserProvider`   | Read authenticated user from SecurityContextHolder  |
| `HeartbeatTracker`      | Track last-seen timestamps in Redis                 |
| `GridRepository`        | Load Grid from MongoDB (for CheckGrid)              |
| `SessionCodeGenerator`  | Generate unique SessionShareCode                    |

---

## 7. Real-Time Layer — WebSocket

### Connection

- Endpoint: `ws://<host>/ws` (STOMP over WebSocket)
- Auth: STOMP CONNECT frame with `Authorization: Bearer <token>` header
- `StompAuthChannelInterceptor` (implements `ExecutorChannelInterceptor`):
  - **CONNECT** (`preSend`): validates JWT, stores `CocroAuthentication` and `shareCode` in WebSocket session attributes.
  - **SEND/SUBSCRIBE** (`beforeHandle`): restores auth to `SecurityContextHolder` on the executor thread.
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
  "participants": [
    { "userId": "...", "username": "Oscar", "status": "JOINED", "isCreator": true },
    { "userId": "...", "username": "Cardamome-Dorée", "status": "JOINED", "isCreator": false }
  ],
  "status": "PLAYING",
  "gridRevision": 0
}
```

The synchronous `@SubscribeMapping` pattern avoids the timing race that would occur
with a `SessionConnectedEvent` approach.

### Ongoing Events

The client then subscribes to `/topic/session/{shareCode}` for broadcasts.

| Event               | Direction  | Destination                  | Trigger                              |
|---------------------|------------|------------------------------|--------------------------------------|
| `ParticipantJoined` | broadcast  | `/topic/session/{shareCode}` | Successful join                      |
| `ParticipantLeft`   | broadcast  | `/topic/session/{shareCode}` | Leave or heartbeat timeout           |
| `GridUpdated`       | broadcast  | `/topic/session/{shareCode}` | Successful grid update               |
| `GridChecked`       | broadcast  | `/topic/session/{shareCode}` | `CheckGridUseCase`                   |
| `SessionEnded`      | broadcast  | `/topic/session/{shareCode}` | Grid complete+correct via CheckGrid  |
| `SessionInterrupted`| broadcast  | `/topic/session/{shareCode}` | Last participant left/timed out      |
| `SyncRequired`      | private    | `/user/queue/session`        | CAS conflict on grid update          |

### Jackson Serialization

`SessionEvent` is a sealed Kotlin interface. Each subtype carries a `"type"` discriminator:

```kotlin
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(SessionEvent.SessionWelcome::class, name = "SessionWelcome"),
    JsonSubTypes.Type(SessionEvent.GridChecked::class, name = "GridChecked"),
    JsonSubTypes.Type(SessionEvent.SessionEnded::class, name = "SessionEnded"),
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
- Migration fallback in `SessionDocumentMapper`: stored `"CREATING"` and `"SCORING"` values are mapped to `PLAYING` on read.

### Redis

Two responsibilities:

1. **Session grid state cache** (`RedisSessionGridStateCache`):
   - Key: `session:{sessionId}:state` → JSON of SessionGridState (TTL: 24h)
   - Key: `session:{sessionId}:lastFlush` → Long (last flushed revision) (TTL: 24h)
   - Key: `sessions:active` → Redis Set of active sessionId strings
   - CAS via Lua script on `revision` field
   - Flushed to MongoDB by `SessionFlushScheduler` and on participant changes

2. **Heartbeat tracker** (`RedisHeartbeatTracker`):
   - Key: `session:{sessionId}:heartbeat:active` → Redis Set of userId strings
   - Key: `session:{sessionId}:heartbeat:away` → Redis Hash { userId → epoch ms timestamp }
   - Read by `HeartbeatTimeoutScheduler`

### JWT

- JJWT library (`JwtTokenIssuer`, `JwtTokenAuthenticator`)
- Claims: `userId`, `username`, `roles`
- Used by both HTTP filter (`JwtAuthenticationFilter`) and STOMP interceptor

---

## 9. Background Schedulers

Both schedulers run inside the BFF application context (Spring `@Scheduled`).

### HeartbeatTimeoutScheduler

- Runs every **15 seconds** (configurable via `cocro.session.heartbeat.check-interval-ms`)
- Grace period: **30 seconds** (configurable via `cocro.session.heartbeat.grace-period-ms`)
- For each tracked session: users away > 30s are evicted via `session.leave(userId)`
- If the last participant times out → `session.interrupt()` → broadcasts `SessionInterrupted`
- Broadcasts `ParticipantLeft { reason: "timeout" }` for each evicted user

### SessionFlushScheduler

- Runs every **30 seconds** (configurable via `cocro.session.flush.idle-check-ms`, code default 60s, overridden to 30s in `application.yml`)
- For each active session: if current `revision > lastFlushedRevision`, writes `SessionGridState` to MongoDB
- Ensures MongoDB is eventually consistent even during bursts of rapid updates

---

## Summary — Happy Path (Creator + 1 Player)

```
Creator                                    Player
  |                                          |
  |-- POST /api/sessions -----------------> |
  |<- 201 { sessionId, shareCode }          |
  |                                          |
  |-- POST /api/sessions/join ------------> |
  |<- 200 SessionFullDto                    |
  |                                          |
  |                                          |-- POST /api/sessions/join -->
  |                                          |<- 200 SessionFullDto (ParticipantJoined broadcast)
  |
  |-- WS CONNECT (JWT + shareCode)          |-- WS CONNECT (JWT + shareCode)
  |-- SUBSCRIBE /app/.../welcome            |-- SUBSCRIBE /app/.../welcome
  |<- SessionWelcome                        |<- SessionWelcome
  |-- SUBSCRIBE /topic/session/...          |-- SUBSCRIBE /topic/session/...
  |
  |-- SEND /app/.../grid ──────────────────► (broadcasts GridUpdated to both)
  |<- GridUpdated                           |<- GridUpdated
  |
  |-- POST /api/sessions/{code}/check ────► (broadcasts GridChecked to both)
  |  if complete+correct:                    (broadcasts SessionEnded to both)
  |<- GridCheckSuccess { isComplete, isCorrect, ... }
```
