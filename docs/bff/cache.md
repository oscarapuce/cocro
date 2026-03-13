# Cache Reference

## Session Grid State Cache

**Port**: `SessionGridStateCache`
**Production implementation**: `RedisSessionGridStateCache`
**Test implementation**: `InMemorySessionGridStateCache`

### Redis Key Schema

```
session:{sessionId}:state      → JSON of SessionGridState           TTL: 24h
session:{sessionId}:lastFlush  → Long (last flushed grid revision)  TTL: 24h
sessions:active                → Redis Set of active sessionId strings
```

### Operations

| Method | Description |
|--------|-------------|
| `get(sessionId)` | Returns `SessionGridState?`, null on miss |
| `set(sessionId, state)` | Unconditional write, resets TTL |
| `compareAndSet(sessionId, expected, new)` | Atomic CAS via Lua script |
| `delete(sessionId)` | Removes state + lastFlush keys |
| `addToActive(sessionId)` | Adds to `sessions:active` set |
| `removeFromActive(sessionId)` | Removes from `sessions:active` set |
| `getAllActiveSessionIds()` | Returns all members of `sessions:active` |

### compareAndSet — Atomic CAS via Lua

`compareAndSet` is atomic via a Lua script executed with `RedisTemplate.execute(RedisScript)`. The script checks that the stored revision equals `expectedRevision` before writing, returning the new revision on success or `-1` on mismatch.

```lua
local current = redis.call('GET', KEYS[1])
if current == false then return -1 end
local parsed = cjson.decode(current)
if parsed['revision'] ~= tonumber(ARGV[1]) then return -1 end
redis.call('SET', KEYS[1], ARGV[2], 'EX', ARGV[3])
return tonumber(ARGV[1]) + 1
```

On `-1` return, the adapter throws `GridStateConflictException`, which `UpdateSessionGridUseCase` catches and maps to `CocroResult.Error(SessionError.GridStateConflict)`. The controller maps this to HTTP 409; the client retries after a `SyncRequired` event.

### Flush Threshold

`UpdateSessionGridUseCase` flushes the in-memory/Redis state to MongoDB when:

```
newRevision - lastFlushedRevision >= flushThreshold   (default: 50)
```

The flush writes the full `SessionGridState` to MongoDB via `SessionRepository.updateGridState()` and updates `session:{sessionId}:lastFlush`.

**Background flush**: `SessionFlushScheduler` runs every 30 seconds and flushes all active sessions regardless of threshold, ensuring MongoDB is eventually consistent even during bursts of rapid updates.

### Cache Miss Fallback

When a use case reads grid state:

```kotlin
val gridState = sessionGridStateCache.get(sessionId) ?: session.sessionGridState
```

If the Redis key has expired (e.g., after a restart), the embedded `sessionGridState` inside the `SessionDocument` is used as a fallback. This state may be up to `flushThreshold` revisions behind, but it is always a valid starting point.

---

## Heartbeat Tracker

**Port**: `HeartbeatTracker`
**Production implementation**: `RedisHeartbeatTracker`

### Redis Key Schema

```
session:{sessionId}:heartbeat:active  → Redis Set of userId strings
session:{sessionId}:heartbeat:away    → Redis Hash  userId → timestamp (epoch ms)
user:{userId}:session                 → String sessionId (reverse lookup)
```

### Operations

| Method | Description |
|--------|-------------|
| `markActive(sessionId, userId)` | Add to active set, remove from away hash |
| `markAway(sessionId, userId)` | Remove from active set, add to away hash with `System.currentTimeMillis()` |
| `isActive(sessionId, userId)` | Check membership in active set |
| `isAway(sessionId, userId)` | Check membership in away hash |
| `getActiveUserIds(sessionId)` | Returns all members of active set |
| `getTimedOutUserIds(sessionId, gracePeriodMs)` | Returns userIds from away hash where `now - timestamp > gracePeriodMs` |
| `registerUserSession(userId, sessionId)` | Write reverse lookup key |
| `unregisterUserSession(userId)` | Delete reverse lookup key |
| `getSessionIdForUser(userId)` | Read reverse lookup key |

### Flow

**User connects** (STOMP CONNECT):
- `StompAuthChannelInterceptor` authenticates and stores `CocroAuthentication`
- `SessionConnectEventListener` calls `heartbeatTracker.markActive(sessionId, userId)`

**User sends heartbeat** (WebSocket SEND to `/app/session/{code}/heartbeat`):
- `SessionWebSocketController` calls `heartbeatTracker.markActive(sessionId, userId)`

**User disconnects** (STOMP DISCONNECT or TCP close):
- `StompSessionEventListener` calls `heartbeatTracker.markAway(sessionId, userId)` with current timestamp

**Reconnect within grace period**:
- `JoinSessionUseCase` detects `heartbeatTracker.isAway(sessionId, userId)` is true
- Calls `heartbeatTracker.markActive(sessionId, userId)` — no domain mutation, no broadcast (transparent reconnection)

### HeartbeatTimeoutScheduler

Runs every **15 seconds**. Grace period: **30 seconds**.

```kotlin
@Scheduled(fixedDelay = 15_000)
fun checkTimeouts() {
    val activeSessions = sessionGridStateCache.getAllActiveSessionIds()
    for (sessionId in activeSessions) {
        val timedOut = heartbeatTracker.getTimedOutUserIds(sessionId, gracePeriodMs = 30_000)
        for (userId in timedOut) {
            leaveSessionUseCase.execute(LeaveSessionDto(sessionId = sessionId, userId = userId))
            heartbeatTracker.unregisterUserSession(UserId(userId))
        }
    }
}
```

When `LeaveSessionUseCase` is invoked by the scheduler, it performs the full leave flow: updates the `Session` aggregate, saves to MongoDB, broadcasts `ParticipantLeft`, and triggers `INTERRUPTED` if the session was the last participant.
