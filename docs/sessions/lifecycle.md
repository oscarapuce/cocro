# Session Lifecycle Reference

> See also: `/docs/sessions/session-ddd.md` for full domain model documentation (Session aggregate, SessionGridState, SessionGridCommand, SessionError definitions).

This file focuses on the session state machine and operational flows.

## State Machine

```
                    ┌──[join]──►  PLAYING  ──[check correct]──► ENDED
POST /api/sessions  │               │
returns shareCode   │           [all leave / timeout]
                    │               │
                    └────────►  INTERRUPTED
```

States:

| State | Description |
|-------|-------------|
| `PLAYING` | Session active; grid updates via WebSocket are accepted |
| `INTERRUPTED` | All participants left (timeout or explicit leave); game paused |
| `ENDED` | Grid was completed and validated as correct via `CheckGridUseCase` |

> **Note:** `CREATING` and `SCORING` states have been removed. `POST /api/sessions` creates the session and returns a lightweight `SessionCreatedResponse { sessionId, shareCode }`. The creator then calls `POST /api/sessions/join` to enter the session and receive the full `SessionFullDto`.

## Transitions

| From | Command | Actor constraint | To | Side effects |
|------|---------|-----------------|-----|-------------|
| (new) | `Join` | any authenticated user | `PLAYING` | `ParticipantJoined` broadcast |
| `PLAYING` | `Join` | previously LEFT participant | `PLAYING` | `ParticipantJoined` broadcast |
| `INTERRUPTED` | `Join` | any previously LEFT participant | `PLAYING` | `ParticipantJoined` broadcast |
| `PLAYING` | `Leave` (not last) | any participant | `PLAYING` | `ParticipantLeft` broadcast |
| `PLAYING` | `Leave` (last participant) | any participant | `INTERRUPTED` | `ParticipantLeft` + `SessionInterrupted` broadcast |
| `PLAYING` | `end()` (grid complete+correct) | system (CheckGrid) | `ENDED` | `SessionEnded` broadcast |

Domain commands are pure functions on the `Session` aggregate returning `CocroResult<Session, SessionError>`.

## REST Endpoints Summary

```
POST /api/sessions                 → create session, returns { sessionId, shareCode }
POST /api/sessions/join            → join session, returns SessionFullDto
POST /api/sessions/leave           → leave session
GET  /api/sessions/{code}/state    → resync full grid state (PLAYER, ADMIN, ANONYMOUS)
POST /api/sessions/{code}/sync     → flush Redis→MongoDB and return SessionFullDto
POST /api/sessions/{code}/check    → validate grid; triggers ENDED if complete+correct
```

## Grid Check Endpoint

`POST /api/sessions/{shareCode}/check` — validates the current session grid state against the reference `Grid` stored in MongoDB.

Flow:

1. Load `Session` from MongoDB by `shareCode`
2. Read current `SessionGridState` from Redis cache (fallback: `session.sessionGridState`)
3. Load reference `Grid` via `gridState.gridShareCode`
4. Call `sessionGridState.checkAgainst(referenceGrid)` — pure domain function
5. Flush grid state to MongoDB + broadcast `GridChecked` event to all participants
6. If `isComplete && isCorrect`: call `session.end()`, save, broadcast `SessionEnded`
7. Return `GridCheckSuccess { isComplete, isCorrect, filledCount, totalCount }`

## Sync Endpoint

`POST /api/sessions/{shareCode}/sync` — flushes the current Redis grid state to MongoDB and returns the full `SessionFullDto` (grid template + current cell state + participant count + status). Requires the caller to be a JOINED participant.

Used by the client to resync after a `SyncRequired` event or after reconnecting.

## WebSocket Events

Events are defined as sealed `SessionEvent` in the BFF application layer, serialized as JSON with `@JsonTypeInfo` / `@JsonSubTypes`.

| Event type | Direction | Topic | Trigger |
|-----------|-----------|-------|---------|
| `SessionWelcome` | private | `/app/session/{code}/welcome` (sync reply via `@SubscribeMapping`) | Client subscribes to `/app/session/{code}/welcome` |
| `ParticipantJoined` | broadcast | `/topic/session/{code}` | Successful `JoinSessionUseCase` |
| `ParticipantLeft` | broadcast | `/topic/session/{code}` | `LeaveSessionUseCase` (manual or timeout) |
| `GridUpdated` | broadcast | `/topic/session/{code}` | Grid cell update via WebSocket command |
| `GridChecked` | broadcast | `/topic/session/{code}` | `CheckGridUseCase` |
| `SessionEnded` | broadcast | `/topic/session/{code}` | `CheckGridUseCase` when grid is complete+correct |
| `SessionInterrupted` | broadcast | `/topic/session/{code}` | Last participant left (explicit or timeout) |
| `SyncRequired` | private | `/user/queue/session` | CAS conflict on grid update (`UpdateSessionGridUseCases`) |

### SessionWelcome Pattern

The welcome message delivers full session state to a newly connected participant. Because `SessionConnectedEvent` fires before the client receives the STOMP CONNECTED frame, a simple event-based welcome would race with client subscription setup.

Solution: the client subscribes to `/app/session/{code}/welcome` inside its `afterConnected` callback. The server handles via `@SubscribeMapping` and returns the `SessionWelcome` synchronously to that subscriber only.

### Jackson Serialization for Sealed Events

`SessionEvent` uses `@JsonTypeInfo(use = Id.NAME, property = "type")`. Each subclass must have `@JsonTypeName("EventName")` — without it, Jackson serializes the inner class name as `"SessionEvent$SessionWelcome"` which clients cannot parse.

```kotlin
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(SessionEvent.SessionWelcome::class, name = "SessionWelcome"),
    JsonSubTypes.Type(SessionEvent.ParticipantJoined::class, name = "ParticipantJoined"),
    // ...
)
sealed interface SessionEvent {
    @JsonTypeName("SessionWelcome")
    data class SessionWelcome(...) : SessionEvent
    // ...
}
```

## Heartbeat and Reconnection

### Client obligations

Clients must send a heartbeat message to `/app/session/{code}/heartbeat` at least every **20 seconds** to stay in `active` state. The server grace period is **30 seconds**.

The Angular frontend sends heartbeats automatically via `SessionStompAdapter.startHeartbeat()` using a 20s `setInterval` started on STOMP connect. The BFF handler is `SessionWebSocketController.handleHeartbeat()`.

### Disconnect detection

The server detects client disconnections via two mechanisms:
1. **STOMP disconnect event** (`StompSessionEventListener`): when the WebSocket closes, the `shareCode` is read from WebSocket session attributes (set during STOMP CONNECT), the session is resolved via `SessionRepository.findByShareCode()`, and the user is immediately moved to "away" status. This approach correctly supports users connected to multiple sessions simultaneously.
2. **Missing heartbeat**: if no heartbeat arrives within the 30s grace period (after a STOMP disconnect), `HeartbeatTimeoutScheduler` evicts the user.

### Server-side flow

1. **Active**: user is in Redis `session:{sessionId}:heartbeat:active` set
2. **Away**: on STOMP disconnect, user moves to `session:{sessionId}:heartbeat:away` hash with timestamp
3. **Timeout**: `HeartbeatTimeoutScheduler` runs every 15s; users in `away` for >30s are evicted — `Session.leave()` is called, and if the last participant times out the session moves to `INTERRUPTED`

### Reconnection within grace period

If a user reconnects (new STOMP CONNECT + join) within the 30s grace period:

1. `JoinSessionUseCase` checks `heartbeatTracker.isAway(sessionId, userId)` → true
2. Calls `heartbeatTracker.markActive(sessionId, userId)` to restore active state
3. **No domain mutation** on `Session` aggregate (participant is still in the list)
4. **No broadcast** (transparent reconnection — other participants are not notified)
5. Returns `JoinSessionSuccess` with current state (client rehydrates)

If the grace period has expired and the user was already evicted, `JoinSessionUseCase` treats them as a new join and performs the full `Join` command + broadcast.

## CAS Conflict Handling

When `compareAndSet()` detects a revision conflict in `UpdateSessionGridUseCases`, the exception is caught and:

1. The current revision is fetched from cache (or DB as fallback)
2. `SyncRequired(currentRevision)` is sent privately to the conflicting user via `sessionNotifier.notifyUser()`
3. The use case returns `CocroResult.Error(SessionError.ConcurrentModification)` (409)
4. Client receives `SyncRequired` and calls `POST /api/sessions/{code}/sync` to rehydrate
