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

`compareAndSet` is atomic via a Lua script executed with `RedisTemplate.execute(RedisScript)`. The script checks that the stored revision equals `expectedRevision` before writing, returning the new revision on success, `-1` if the key is missing, or `-2` on revision mismatch.

```lua
local current = redis.call('GET', KEYS[1])
if not current then
  return -1
end
local state = cjson.decode(current)
if tostring(state['revision']) ~= ARGV[1] then
  return -2
end
redis.call('SET', KEYS[1], ARGV[2])
redis.call('EXPIRE', KEYS[1], ARGV[3])
return tonumber(ARGV[4])
```

On `-1` (key missing) or `-2` (revision conflict), the adapter throws `IllegalStateException`, which `UpdateSessionGridUseCase` catches and maps to `CocroResult.Error(SessionError.ConcurrentModification)`. The controller maps this to HTTP 409; the client retries after a `SyncRequired` event.

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
```

### Operations

| Method | Description |
|--------|-------------|
| `markActive(sessionId, userId)` | Add to active set, remove from away hash |
| `markAway(sessionId, userId)` | Remove from active set, add to away hash with `System.currentTimeMillis()` |
| `remove(sessionId, userId)` | Remove from both active and away (explicit leave or timeout) |
| `isAway(sessionId, userId)` | Check membership in away hash |
| `getTimedOutUserIds(sessionId, gracePeriodMs)` | Returns userIds from away hash where `now - timestamp > gracePeriodMs` |

### Flow

**User connects** (STOMP CONNECT):
- `StompAuthChannelInterceptor` authenticates and stores `CocroAuthentication`
- The `shareCode` is stored in WebSocket session attributes (`SESSION_SHARE_CODE_KEY`)

**User sends heartbeat** (WebSocket SEND to `/app/session/{code}/heartbeat`):
- `SessionWebSocketController` calls `heartbeatTracker.markActive(sessionId, userId)`

**User disconnects** (STOMP DISCONNECT or TCP close):
- `StompSessionEventListener` reads the `shareCode` from WebSocket **session attributes** (not a Redis reverse lookup)
- Resolves the session via `sessionRepository.findByShareCode(shareCode)`
- Calls `heartbeatTracker.markAway(sessionId, userId)` — this correctly supports users connected to multiple sessions simultaneously (each WebSocket connection carries its own shareCode)

**Reconnect within grace period**:
- `JoinSessionUseCase` detects `heartbeatTracker.isAway(sessionId, userId)` is true
- Calls `heartbeatTracker.markActive(sessionId, userId)` — no domain mutation, no broadcast (transparent reconnection)

**Session ends** (grid complete + correct):
- `CheckGridUseCase` calls `heartbeatTracker.remove(sessionId, userId)` for all participants
- `sessionGridStateCache.deactivate(sessionId)` removes the session from the active set

### HeartbeatTimeoutScheduler

Runs every **15 seconds**. Grace period: **30 seconds**.

```kotlin
@Scheduled(fixedDelay = 15_000)
fun checkTimeouts() {
    val activeSessions = sessionGridStateCache.getActiveSessions()
    for (sessionId in activeSessions) {
        val timedOut = heartbeatTracker.getTimedOutUserIds(sessionId, gracePeriodMs = 30_000)
        for (userId in timedOut) {
            session.leave(userId)
            heartbeatTracker.remove(sessionId, userId)
        }
        // If all participants timed out → session.interrupt()
    }
}
```

When a user is evicted by the scheduler, it performs the full leave flow: updates the `Session` aggregate, saves to MongoDB, broadcasts `ParticipantLeft { reason: "timeout" }`, and triggers `INTERRUPTED` if all participants have left.
