# Session Lifecycle Reference

> See also: `/docs/session-ddd.md` for full domain model documentation (Session aggregate, SessionGridState, SessionGridCommand, SessionError definitions).

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
| `SessionWelcome` | private | `/user/queue/session` | Client subscribes to `/app/session/{code}/welcome` |
| `ParticipantJoined` | broadcast | `/topic/session/{code}` | Successful `JoinSessionUseCase` |
| `ParticipantLeft` | broadcast | `/topic/session/{code}` | `LeaveSessionUseCase` (manual or timeout) |
| `GridUpdated` | broadcast | `/topic/session/{code}` | Grid cell update via WebSocket command |
| `GridChecked` | broadcast | `/topic/session/{code}` | `CheckGridUseCase` |
| `SessionEnded` | broadcast | `/topic/session/{code}` | `CheckGridUseCase` when grid is complete+correct |
| `SessionInterrupted` | broadcast | `/topic/session/{code}` | Last participant left (explicit or timeout) |
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
3. **Timeout**: `HeartbeatTimeoutScheduler` runs every 15s; users in `away` for >30s are evicted — `Session.leave()` is called, and if the last participant times out the session moves to `INTERRUPTED`

### Reconnection within grace period

If a user reconnects (new STOMP CONNECT + join) within the 30s grace period:

1. `JoinSessionUseCase` checks `heartbeatTracker.isAway(sessionId, userId)` → true
2. Calls `heartbeatTracker.markActive(sessionId, userId)` to restore active state
3. **No domain mutation** on `Session` aggregate (participant is still in the list)
4. **No broadcast** (transparent reconnection — other participants are not notified)
5. Returns `JoinSessionSuccess` with current state (client rehydrates)

If the grace period has expired and the user was already evicted, `JoinSessionUseCase` treats them as a new join and performs the full `Join` command + broadcast.

## Planned Features (not yet implemented)

### SyncRequired event

The `SyncRequired` event type is defined in `SessionEvent` but is not yet sent by `UpdateSessionGridUseCases`. Currently, a CAS conflict on `compareAndSet()` throws an exception that bubbles up unhandled. The intended behavior:

- Catch the CAS conflict exception in `UpdateSessionGridUseCases`
- Send `SyncRequired(currentRevision)` privately to the conflicting user
- Client calls `POST /api/sessions/{code}/sync` to resync
