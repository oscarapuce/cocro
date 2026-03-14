# Session Lifecycle Reference

> See also: `/docs/session-ddd.md` for full domain model documentation (Session aggregate, SessionGridState, SessionGridCommand, SessionError definitions).

This file focuses on the session state machine and operational flows.

## State Machine

```
CREATING ──[start]──► PLAYING
                         │
                      [all leave]
                         │
                         ▼
                     INTERRUPTED
```

States:

| State | Description |
|-------|-------------|
| `CREATING` | Session created, waiting for participants; grid not started |
| `PLAYING` | Session started by creator; grid updates via WebSocket are accepted |
| `INTERRUPTED` | All participants left; game paused (may be resumable in future) |

## Transitions

| From | Command | Actor constraint | To | Side effects |
|------|---------|-----------------|-----|-------------|
| `CREATING` | `Join` | any authenticated user | `CREATING` | `ParticipantJoined` broadcast |
| `CREATING` | `Start` | creator only | `PLAYING` | `SessionStarted` broadcast |
| `PLAYING` | `Leave` (last participant) | any participant | `INTERRUPTED` | `ParticipantLeft` broadcast |
| `PLAYING` | `Leave` (not last) | any participant | `PLAYING` | `ParticipantLeft` broadcast |
Domain commands are defined as `SessionLifecycleCommand` in `cocro-shared`. The `Session.apply(command)` function is a pure function returning `CocroResult<Session, SessionError>`.

## REST Endpoints Summary

```
POST /api/sessions                 → create session (PLAYER, ADMIN)
POST /api/sessions/join            → join session (PLAYER, ADMIN, ANONYMOUS)
POST /api/sessions/leave           → leave session (PLAYER, ADMIN, ANONYMOUS)
POST /api/sessions/start           → start session (PLAYER, ADMIN — creator only)
GET  /api/sessions/{code}/state    → resync full grid state (PLAYER, ADMIN, ANONYMOUS)
POST /api/sessions/{code}/check    → validate grid against reference (PLAYER, ADMIN, ANONYMOUS)
```

## Grid Check Endpoint

`POST /api/sessions/{shareCode}/check` — validates the current session grid state against the reference `Grid` stored in MongoDB. This endpoint is read-only and idempotent.

Flow:

1. Load `Session` from MongoDB by `shareCode`
2. Read current `SessionGridState` from Redis cache (fallback: `session.sessionGridState`)
3. Load reference `Grid` via `gridState.gridShareCode`
4. Call `sessionGridState.checkAgainst(referenceGrid)` — pure domain function in `cocro-shared`
5. Return `GridCheckSuccess { isComplete, isCorrect, filledCount, totalCount }`

No state transition is triggered. Clients may call this at any time during `PLAYING`.

> **Note:** `CheckGridUseCase` is read-only and does not trigger any state transition. The `GridCheckResult` is returned to the caller for display purposes only.

## WebSocket Events

Events are defined as sealed `SessionEvent` in the BFF application layer, serialized as JSON with `@JsonTypeInfo` / `@JsonSubTypes`.

| Event type | Direction | Topic | Trigger |
|-----------|-----------|-------|---------|
| `SessionWelcome` | private | `/user/queue/session` | Client subscribes to `/app/session/{code}/welcome` |
| `ParticipantJoined` | broadcast | `/topic/session/{code}` | Successful `JoinSessionUseCase` |
| `ParticipantLeft` | broadcast | `/topic/session/{code}` | `LeaveSessionUseCase` (manual or timeout) |
| `SessionStarted` | broadcast | `/topic/session/{code}` | `StartSessionUseCase` |
| `GridUpdated` | broadcast | `/topic/session/{code}` | Grid cell update via WebSocket command |
| `SyncRequired` | private | `/user/queue/session` | CAS conflict on grid update — **defined but not yet sent** |

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

Clients must send a heartbeat message to `/app/session/{code}/heartbeat` at least every **30 seconds** to stay in `active` state.

### Server-side flow

1. **Active**: user is in Redis `session:{sessionId}:heartbeat:active` set
2. **Away**: on STOMP disconnect, user moves to `session:{sessionId}:heartbeat:away` hash with timestamp
3. **Timeout**: `HeartbeatTimeoutScheduler` runs every 15s; users in `away` for >30s are evicted via `LeaveSessionUseCase`

### Reconnection within grace period

If a user reconnects (new STOMP CONNECT + join) within the 30s grace period:

1. `JoinSessionUseCase` checks `heartbeatTracker.isAway(sessionId, userId)` → true
2. Calls `heartbeatTracker.markActive(sessionId, userId)` to restore active state
3. **No domain mutation** on `Session` aggregate (participant is still in the list)
4. **No broadcast** (transparent reconnection — other participants are not notified)
5. Returns `JoinSessionSuccess` with current state (client rehydrates)

If the grace period has expired and the user was already evicted, `JoinSessionUseCase` treats them as a new join and performs the full `Join` command + broadcast.

## Planned Features (not yet implemented)

### SCORING state

A future `SCORING` state will be added to the session lifecycle. The intended flow:

```
PLAYING ──[check correct]──► SCORING ──► ENDED
```

- When `CheckGridUseCase` detects `isComplete && isCorrect`, it would trigger a `PLAYING → SCORING` transition on the `Session` aggregate
- A new `SessionLifecycleCommand.Score` command would be added to `cocro-shared`
- A `SessionCompleted` event would be broadcast to all participants
- This is a **nice-to-have for MVP** — currently `CheckGridUseCase` is read-only

### SyncRequired event

The `SyncRequired` event type is defined in `SessionEvent` but is not yet sent by `UpdateSessionGridUseCases`. Currently, a CAS conflict on `compareAndSet()` throws an exception that bubbles up unhandled. The intended behavior:

- Catch the CAS conflict exception in `UpdateSessionGridUseCases`
- Send `SyncRequired(currentRevision)` privately to the conflicting user
- Client calls `GET /api/sessions/{code}/state` to resync

### End session endpoint

`Session.end()` exists in the domain model but no use case or controller exposes it. A future `POST /api/sessions/{code}/end` endpoint would allow the creator to manually end a session.
