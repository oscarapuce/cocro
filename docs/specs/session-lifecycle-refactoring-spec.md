# Session Lifecycle Refactoring -- Functional Specification

**Version:** 1.0
**Date:** 2026-03-26
**Status:** DRAFT -- requires lead dev decisions on marked items
**Scope:** cocro-shared (domain) + cocro-bff (application/presentation) + cocro-angular (all layers)

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [Session Status Simplification](#2-session-status-simplification)
3. [Use Case: CreateSession](#3-use-case-createsession)
4. [Use Case: JoinSession](#4-use-case-joinsession)
5. [Use Case: LeaveSession](#5-use-case-leavesession)
6. [Use Case: SynchroniseSession (NEW)](#6-use-case-synchronisesession-new)
7. [Use Case: CheckGrid (extended)](#7-use-case-checkgrid-extended)
8. [WebSocket Event Contract](#8-websocket-event-contract)
9. [Domain Model Impact (cocro-shared)](#9-domain-model-impact-cocro-shared)
10. [Application Layer Impact (cocro-bff)](#10-application-layer-impact-cocro-bff)
11. [Angular Frontend Impact](#11-angular-frontend-impact)
12. [Business Rules Summary](#12-business-rules-summary)
13. [Decisions Required](#13-decisions-required)
14. [Migration / Backward Compatibility](#14-migration--backward-compatibility)

---

## 1. Executive Summary

This refactoring restructures the session lifecycle around a clean separation between **creation** (lightweight, returns a share code) and **joining** (full initialization, returns the complete session state). The creator is no longer auto-joined at creation time -- the creator must explicitly call Join like any other participant. This eliminates the asymmetry between the creator flow and the joiner flow.

Key changes:

- `SessionStatus` reduced from 5 values to 3: `PLAYING`, `INTERRUPTED`, `ENDED`.
- `CreateSession` becomes a thin factory: no participant, no heartbeat, returns `{ sessionId, shareCode }`.
- `JoinSession` becomes the single entry point for getting the full session state.
- A new `SynchroniseSession` use case flushes Redis to MongoDB and returns `SessionFullDto`.
- `CheckGrid` triggers `Session.end()` when the grid is complete and correct.
- A new `SessionEnded` WebSocket event is introduced.

---

## 2. Session Status Simplification

### Current

```
CREATING -> PLAYING -> SCORING -> ENDED
                    -> INTERRUPTED
```

### Target

```
PLAYING -> ENDED
        -> INTERRUPTED
```

| Status        | Meaning                                         |
|---------------|--------------------------------------------------|
| `PLAYING`     | Session is active, participants can place letters |
| `INTERRUPTED` | All participants have left (or timeout), session is frozen but recoverable |
| `ENDED`       | Grid was completed correctly, session is finished and read-only |

### Removal justification

- **CREATING**: No longer needed. Sessions are created directly in PLAYING status (already the case in `Session.create()`). There is no multi-step creation wizard.
- **SCORING**: Never used in current code. The grid check is instantaneous. If a scoring phase is needed later, it can be reintroduced.

### Impact on existing code references to CREATING/SCORING

| Location | Current reference | Change |
|----------|-------------------|--------|
| `Session.applyJoin()` | Allows join on `CREATING` or `PLAYING` | Allow join on `PLAYING` only |
| `Session.end()` | Allows end on `CREATING`, `PLAYING`, `SCORING` | Allow end on `PLAYING` only |
| `SessionApplyTest` | Tests `CREATING` status | Remove test, add equivalent `PLAYING` test if missing |
| `SessionEndTest` | Tests `CREATING` and `SCORING` end | Remove those tests |
| Angular `SessionStatus` type | Includes `'CREATING'` and `'SCORING'` | Remove from union type |
| Angular `GridPlayerComponent` | Initializes status to `'CREATING'` | Initialize to `'PLAYING'` |

---

## 3. Use Case: CreateSession

### Summary

Creates a new session entity without any participant. Returns a lightweight response containing only the session identifier and share code.

### Input

```
CreateSessionDto { gridId: String }
```

### Output (changed)

```
SessionCreationSuccess { sessionId: String, shareCode: String }
```

**Previously:** Returned `SessionFullDto` (with gridTemplate, cells, participantCount, etc.)
**Now:** Returns only the identifiers needed to navigate or share the session link.

### Happy Path

```
1. Authenticate caller -> UserId (creator)
2. Validate gridId format
3. Load Grid from repository by GridShareCode(gridId)
4. Generate unique SessionShareCode
5. Call Session.create(creatorId, shareCode, gridId, gridTemplate)
   -- Session is created with:
      - status = PLAYING
      - participants = []          <-- CHANGED: no longer [Participant.creator(creatorId)]
      - sessionGridState = initial (empty cells, revision 0)
      - gridTemplate = snapshot
6. Save session to MongoDB
7. Initialize Redis cache: sessionGridStateCache.initialize(sessionId, initialState)
8. DO NOT register heartbeat           <-- CHANGED
9. Return SessionCreationSuccess { sessionId, shareCode }
```

### Error Cases

| Condition | Error | HTTP |
|-----------|-------|------|
| User not authenticated | `SessionError.Unauthorized` | 401 |
| Invalid gridId format | Validation errors | 400 |
| Grid not found | `SessionError.ReferenceGridNotFound` | 404 |

### What is NOT done at creation

- No participant added (creator is NOT joined)
- No heartbeat tracking
- No WebSocket event broadcast
- No `SessionFullDto` construction

### Frontend Flow After Create

```
Angular CreateSessionUseCase:
  1. POST /api/sessions { gridId } -> { sessionId, shareCode }
  2. Navigate to /play/{shareCode}
  3. GridPlayerComponent.ngOnInit() calls JoinSession
```

This means the Angular `CreateSessionUseCase` no longer needs to chain with a WebSocket connection. It simply creates then navigates. The `ngOnInit` of the play component handles join + WS.

---

## 4. Use Case: JoinSession

### Summary

Adds the caller as a participant (or reconnects them), flushes the current grid state from Redis to MongoDB, and returns the complete session payload.

### Input

```
JoinSessionDto { shareCode: String }
```

### Output

```
SessionFullDto {
  sessionId: String,
  shareCode: String,
  status: String,
  participantCount: Int,
  topicToSubscribe: String,
  gridTemplate: GridTemplateDto,
  gridRevision: Long,
  cells: List<CellStateDto>,
}
```

### Scenarios

#### 4.1 -- New Join (happy path)

Precondition: User is not in the participants list at all.

```
1. Authenticate caller -> UserId
2. Validate shareCode format
3. Load session by shareCode
4. Check transparent reconnection: heartbeatTracker.isAway(sessionId, userId)
   -- If away: mark active, return SessionFullDto (no domain command, no broadcast)
5. Apply domain command: Session.apply(SessionLifecycleCommand.Join(userId))
6. Save updated session to MongoDB
7. Flush Redis state to MongoDB:
   sessionGridStateCache.get(sessionId) -> if present, updateGridState + markFlushed
8. Register heartbeat: markActive + registerUserSession
9. Broadcast: ParticipantJoined { userId, participantCount }
10. Return SessionFullDto
```

#### 4.2 -- Idempotent Join (user already JOINED)

Precondition: User is already in participants with status `JOINED`.

The domain `Session.apply(Join)` returns `AlreadyParticipant` error. The use case catches this, checks that the user is actively JOINED, and returns `SessionFullDto` with the current state.

**Current behavior:** Returns `SessionFullDto`.
**Decision confirmed:** Keep returning `SessionFullDto` (not a 200 empty body). Rationale: the client (GridPlayerComponent) always expects a full DTO to initialize its grid. A 200-empty response would require the client to handle a separate "I'm already in" code path.

No `ParticipantJoined` event is broadcast in the idempotent case (to avoid duplicate notifications to other participants).

#### 4.3 -- Rejoin After LEFT

Precondition: User is in participants list with status `LEFT`.

**Current behavior:** The domain `Session.applyJoin()` checks `participants.any { it.userId == actorId }`. This returns `true` for a LEFT participant, so it returns `AlreadyParticipant` error. The use case idempotent handler then checks `status == JOINED`, which is false, so it propagates the error.

**DECISION REQUIRED [D-1]:** How should rejoin-after-LEFT work?

**Option A (recommended):** Modify `Session.applyJoin()` to allow users with status `LEFT` to rejoin. Specifically, change the duplicate check from `participants.any { it.userId == actorId }` to `participants.any { it.userId == actorId && it.status == InviteStatus.JOINED }`. Then `join()` would add a new `Participant.joined(actorId)` entry, or better, update the existing entry's status back to `JOINED`.

**Option B:** Remove the old `LEFT` entry and add a fresh `Participant.joined(actorId)` entry.

Recommendation: Option A with in-place status update. The `join()` method should check if the user already has an entry with status `LEFT`, and if so, flip it back to `JOINED`. If no entry exists, add a new one.

```kotlin
// Proposed logic in Session.join():
fun join(actorId: UserId, now: Instant = Instant.now()): Session {
    val existingLeft = participants.indexOfFirst { it.userId == actorId && it.status == InviteStatus.LEFT }
    val updatedParticipants = if (existingLeft >= 0) {
        participants.mapIndexed { i, p -> if (i == existingLeft) p.copy(status = InviteStatus.JOINED) else p }
    } else {
        participants + Participant.joined(actorId)
    }
    return copy(participants = updatedParticipants, updatedAt = now)
}
```

#### 4.4 -- Session Full

Precondition: `ParticipantsRule.canJoin(participants)` returns `false`.

Returns `SessionError.SessionFull` (HTTP 409).

Note: `countActiveParticipants` counts only `JOINED` participants. A `LEFT` participant does NOT count toward the limit. This means if 4 users joined and 1 left, a new user (or the left user) CAN join.

#### 4.5 -- Session Not Found

Returns `SessionError.SessionNotFound` (HTTP 403 per current ErrorCode mapping).

#### 4.6 -- Session ENDED or INTERRUPTED

**Current behavior of `Session.applyJoin()`:** Only allows join when status is `CREATING` or `PLAYING`.

**After refactoring:** Only allows join when status is `PLAYING`.

**DECISION REQUIRED [D-2]:** Should a user be allowed to join (or rejoin) an `INTERRUPTED` session?

**Option A:** No. INTERRUPTED means the session is frozen. A new user cannot join. But the creator could "resume" it (future use case).

**Option B (recommended):** Yes, joining an INTERRUPTED session re-activates it. Rationale: INTERRUPTED is triggered when all participants leave. If someone comes back, the session should resume automatically.

If Option B: `Session.applyJoin()` accepts `PLAYING` and `INTERRUPTED`. On join into an INTERRUPTED session, the status flips back to `PLAYING`:

```kotlin
private fun applyJoin(actorId: UserId): CocroResult<Session, SessionError> {
    if (status !in setOf(SessionStatus.PLAYING, SessionStatus.INTERRUPTED)) {
        return err(SessionError.InvalidStatusForAction(status, "join"))
    }
    // ...existing checks...
    val joined = join(actorId)
    val resumed = if (status == SessionStatus.INTERRUPTED) joined.copy(status = SessionStatus.PLAYING) else joined
    return ok(resumed)
}
```

#### 4.7 -- Transparent Reconnection (STOMP disconnect + reconnect within grace period)

Already implemented. `heartbeatTracker.isAway()` check happens before domain command. If the user is "away", they are silently re-activated without a `ParticipantJoined` broadcast.

---

## 5. Use Case: LeaveSession

### Summary

Marks the caller as LEFT in the session, flushes grid state, cleans up heartbeat, broadcasts departure.

### Input

```
LeaveSessionDto { shareCode: String }
```

### Output

```
SessionLeaveSuccess { sessionId: String }
```

### Scenarios

#### 5.1 -- Explicit Leave (user clicks Leave button)

```
1. Authenticate caller -> UserId
2. Validate shareCode format
3. Load session by shareCode
4. Apply domain command: Session.apply(SessionLifecycleCommand.Leave(userId))
5. Save updated session to MongoDB
6. Flush Redis state to MongoDB
7. Clean up heartbeat: remove + unregisterUserSession
8. Broadcast: ParticipantLeft { userId, participantCount, reason="explicit" }
9. Check if all participants are now LEFT:
   -- If yes: session.interrupt() -> save -> broadcast SessionInterrupted (NEW, see D-3)
10. Return SessionLeaveSuccess
```

#### 5.2 -- Timeout Leave (heartbeat scheduler)

Triggered by `HeartbeatTimeoutScheduler` when `getTimedOutUserIds()` returns users past the grace period.

```
1. Scheduler finds timed-out users
2. For each timed-out user: session.leave(userId)
3. Save updated session
4. Flush Redis state
5. Clean up heartbeat
6. Broadcast: ParticipantLeft { userId, participantCount, reason="timeout" }
7. Check if all participants are now LEFT:
   -- If yes: session.interrupt() -> save -> broadcast SessionInterrupted (NEW, see D-3)
```

**DECISION REQUIRED [D-3]:** Should there be a `SessionInterrupted` event?

Currently, when the last participant leaves (or times out), the session transitions to INTERRUPTED, but no explicit event is broadcast. The remaining participants see individual `ParticipantLeft` events, but the session status change is silent.

**Recommendation:** Add a `SessionInterrupted` event broadcast after the last active participant leaves. This allows the Angular client to show an appropriate message ("Session interrupted -- all participants left") and disable input. Without this event, the client would only know via a subsequent JoinSession call that the status changed.

#### 5.3 -- Error: User Not a Participant

Returns `SessionError.UserNotParticipant` (HTTP 403).

#### 5.4 -- Error: User Already Left

Returns `SessionError.UserNotParticipant` because `applyLeave()` checks for `status == InviteStatus.JOINED`.

#### 5.5 -- Creator Leaves

No special treatment. The creator is a participant like any other. They can leave and rejoin.

**DECISION REQUIRED [D-4]:** Should the creator leaving have any special effect?

**Recommendation:** No. The creator's identity (`creatorId`) is preserved on the session regardless. It is used only for authorization on specific actions (currently `Session.end()`). If `Session.end()` is decoupled from creator-only access (see Section 7), there is no behavioral difference.

---

## 6. Use Case: SynchroniseSession (NEW)

### Summary

Flushes the latest Redis state to MongoDB and returns the complete session DTO. Called by the client when it receives a `SyncRequired` WebSocket event.

### Distinction from GetSessionStateUseCase

| Aspect | GetSessionState | SynchroniseSession |
|--------|-----------------|-------------------|
| Flushes Redis -> MongoDB | No | Yes |
| Returns | `SessionStateDto` (cells only) | `SessionFullDto` (full session) |
| Use case | Lightweight resync of cells | Full session resync after disruption |
| Trigger | Client detects revision gap | `SyncRequired` WebSocket event |

### Input

```
shareCode: String (path variable)
```

### Output

```
SessionFullDto (same structure as JoinSession response)
```

### Happy Path

```
1. Authenticate caller -> UserId
2. Validate shareCode format
3. Load session by shareCode
4. Verify caller is JOINED participant
5. Verify session status is PLAYING or INTERRUPTED
6. Load grid state from Redis (fallback: session.sessionGridState)
7. Flush to MongoDB: sessionRepository.updateGridState(sessionId, gridState)
8. Mark flushed: sessionGridStateCache.markFlushed(sessionId, revision)
9. Build and return SessionFullDto
```

### Error Cases

| Condition | Error | HTTP |
|-----------|-------|------|
| User not authenticated | `SessionError.Unauthorized` | 401 |
| Invalid shareCode | `SessionError.InvalidShareCode` | 400 |
| Session not found | `SessionError.SessionNotFound` | 403 |
| User not participant | `SessionError.UserNotParticipant` | 403 |
| Session ENDED | `SessionError.InvalidStatusForAction` | 400 |

### REST Endpoint

```
POST /api/sessions/{shareCode}/sync
```

**DECISION REQUIRED [D-5]:** GET or POST for sync?

POST is recommended because the operation has a side effect (flush). Alternatively, GET could be used if we consider the flush an implementation detail. Current `GetSessionState` is a GET.

---

## 7. Use Case: CheckGrid (extended)

### Summary

Compares the current session grid state against the reference grid. If the grid is complete and correct, transitions the session to ENDED and broadcasts `SessionEnded`.

### Input

```
shareCode: String (path variable)
```

### Output

```
GridCheckSuccess {
  shareCode: String,
  isComplete: Boolean,
  isCorrect: Boolean,
  filledCount: Int,
  totalCount: Int,
  wrongCount: Int,
  correctCount: Int,
}
```

### Happy Path (grid not yet complete)

Same as current behavior:

```
1-7. [Same: auth, validate, load session, verify participant, verify PLAYING, load state, load reference]
8. gridState.checkAgainst(referenceGrid) -> GridCheckResult
9. Flush state to MongoDB
10. Broadcast: GridChecked { userId, isComplete=false, correctCount, totalCount }
11. Return GridCheckSuccess
```

### Happy Path (grid complete AND correct -- END-OF-GAME)

```
1-8. [Same as above]
9. GridCheckResult.isComplete == true && GridCheckResult.isCorrect == true
10. Flush state to MongoDB
11. Transition session: session.end(???) -> save to MongoDB
12. Broadcast: GridChecked { userId, isComplete=true, correctCount, totalCount }
13. Broadcast: SessionEnded { shareCode, correctCount, totalCount }   <-- NEW
14. Return GridCheckSuccess { isComplete=true, isCorrect=true, ... }
```

**DECISION REQUIRED [D-6]:** Who calls `Session.end()` in CheckGrid?

Currently `Session.end()` requires `isCreator(actorId)`. But in CheckGrid, any JOINED participant can trigger the check that discovers the grid is complete. Options:

**Option A (recommended):** Add a new method `Session.endByCompletion()` that does not check creator identity. The invariant is: the grid is complete and correct, which is an objective domain fact, not an authorization concern. The existing `Session.end(actorId)` can be kept for a future "creator aborts session" use case.

```kotlin
fun endByCompletion(now: Instant = Instant.now()): CocroResult<Session, SessionError> {
    if (status != SessionStatus.PLAYING) {
        return err(SessionError.InvalidStatusForAction(status, "end"))
    }
    return ok(copy(status = SessionStatus.ENDED, updatedAt = now))
}
```

**Option B:** Remove the `isCreator` check from `Session.end()` entirely. This is simpler but removes the ability to restrict manual session ending to the creator.

**Option C:** Always use the session's `creatorId` as the actor when calling from CheckGrid. This preserves the existing method signature but is semantically dishonest.

### CheckGrid on INTERRUPTED session

**DECISION REQUIRED [D-7]:** Can CheckGrid be called on an INTERRUPTED session?

**Option A (recommended):** No. If the session is INTERRUPTED, nobody should be checking the grid. The current guard `session.status != SessionStatus.PLAYING` already rejects this. This is consistent: INTERRUPTED means no active participants.

**Option B:** Yes, allow it. This would be an edge case where a participant just reconnected and hasn't triggered a JoinSession yet. But since Join is required before any action, this scenario should not occur.

### CheckGrid on ENDED session

Rejected with `SessionError.InvalidStatusForAction(ENDED, "check-grid")`. The grid is already completed; checking again is unnecessary.

---

## 8. WebSocket Event Contract

### Events to KEEP (unchanged)

| Event | Description | Payload |
|-------|-------------|---------|
| `SessionWelcome` | Sent on `@SubscribeMapping` or via `SessionConnectedEvent` | `{ shareCode, topicToSubscribe, participantCount, status, gridRevision }` |
| `ParticipantJoined` | Broadcast when a new participant joins | `{ userId, participantCount }` |
| `ParticipantLeft` | Broadcast when a participant leaves or times out | `{ userId, participantCount, reason }` |
| `GridUpdated` | Broadcast on each cell change | `{ actorId, posX, posY, commandType, letter? }` |
| `SyncRequired` | Sent privately when client revision is stale | `{ currentRevision }` |
| `GridChecked` | Broadcast on check result | `{ userId, isComplete, correctCount, totalCount }` |

### Events to ADD

| Event | Description | Payload |
|-------|-------------|---------|
| `SessionEnded` | Broadcast when CheckGrid determines isComplete && isCorrect | `{ shareCode, correctCount, totalCount }` |
| `SessionInterrupted` (see D-3) | Broadcast when last participant leaves | `{ shareCode }` |

#### SessionEnded -- JSON wire format

```json
{
  "type": "SessionEnded",
  "shareCode": "AB12",
  "correctCount": 42,
  "totalCount": 42
}
```

#### SessionInterrupted -- JSON wire format (if D-3 accepted)

```json
{
  "type": "SessionInterrupted",
  "shareCode": "AB12"
}
```

### Events to MODIFY

| Event | Change |
|-------|--------|
| `SessionWelcome.status` | No longer emits `"CREATING"` or `"SCORING"` values |
| `GridChecked` | No payload change, but now may be followed by `SessionEnded` |

### Jackson @JsonSubTypes Update Required

```kotlin
@JsonSubTypes(
    // existing...
    JsonSubTypes.Type(SessionEvent.SessionEnded::class, name = "SessionEnded"),
    JsonSubTypes.Type(SessionEvent.SessionInterrupted::class, name = "SessionInterrupted"), // if D-3
)
```

---

## 9. Domain Model Impact (cocro-shared)

### 9.1 -- SessionStatus.kt

```kotlin
enum class SessionStatus {
    PLAYING,
    INTERRUPTED,
    ENDED,
}
// Remove: CREATING, SCORING
```

### 9.2 -- Session.kt

#### `Session.create()` -- Remove creator from participants

```kotlin
fun create(...): Session {
    return Session(
        ...
        status = SessionStatus.PLAYING,
        participants = emptyList(),        // CHANGED from listOf(Participant.creator(creatorId))
        ...
    )
}
```

#### `Session.rehydrate()` -- Remove creator-must-be-participant invariant

```kotlin
fun rehydrate(...): Session {
    // REMOVE: require(participants.any { it.userId == creatorId })
    require(participants.size <= ParticipantsRule.MAX_ACTIVE_PARTICIPANTS)
    return Session(...)
}
```

**Rationale:** The creator is no longer automatically a participant. A session may have zero participants (freshly created, before anyone joins). The `creatorId` field still exists on the session for authorization purposes, but it is decoupled from the participants list.

#### `Session.applyJoin()` -- Update status guard and handle rejoin-after-LEFT

```kotlin
private fun applyJoin(actorId: UserId): CocroResult<Session, SessionError> {
    if (status !in setOf(SessionStatus.PLAYING, SessionStatus.INTERRUPTED)) {  // CHANGED
        return err(SessionError.InvalidStatusForAction(status, "join"))
    }
    val existingParticipant = participants.find { it.userId == actorId }
    if (existingParticipant != null && existingParticipant.status == InviteStatus.JOINED) {
        return err(SessionError.AlreadyParticipant(actorId.toString(), shareCode.value))
    }
    // LEFT participant: allowed to rejoin (not counted in capacity until re-JOINED)
    if (!ParticipantsRule.canJoin(participants) && existingParticipant?.status != InviteStatus.LEFT) {
        return err(SessionError.SessionFull)
    }
    val updated = join(actorId)
    // Resume from INTERRUPTED if needed (decision D-2)
    val resumed = if (status == SessionStatus.INTERRUPTED) updated.copy(status = SessionStatus.PLAYING) else updated
    return ok(resumed)
}
```

#### `Session.join()` -- Handle rejoin (flip LEFT -> JOINED)

```kotlin
fun join(actorId: UserId, now: Instant = Instant.now()): Session {
    val existingIndex = participants.indexOfFirst { it.userId == actorId && it.status == InviteStatus.LEFT }
    val updatedParticipants = if (existingIndex >= 0) {
        participants.mapIndexed { i, p ->
            if (i == existingIndex) p.copy(status = InviteStatus.JOINED) else p
        }
    } else {
        participants + Participant.joined(actorId)
    }
    return copy(participants = updatedParticipants, updatedAt = now)
}
```

#### `Session.end()` -- Update status guard

```kotlin
fun end(actorId: UserId, now: Instant = Instant.now()): CocroResult<Session, SessionError> {
    if (!isCreator(actorId)) {
        return err(SessionError.NotCreator(creatorId.toString(), actorId.toString()))
    }
    if (status != SessionStatus.PLAYING) {  // CHANGED: only PLAYING
        return err(SessionError.InvalidStatusForAction(status, "end"))
    }
    return ok(copy(status = SessionStatus.ENDED, updatedAt = now))
}
```

#### New `Session.endByCompletion()` (if D-6 = Option A)

```kotlin
fun endByCompletion(now: Instant = Instant.now()): CocroResult<Session, SessionError> {
    if (status != SessionStatus.PLAYING) {
        return err(SessionError.InvalidStatusForAction(status, "end"))
    }
    return ok(copy(status = SessionStatus.ENDED, updatedAt = now))
}
```

### 9.3 -- Participant.kt

`Participant.creator()` factory method can be kept or removed.

**Recommendation:** Remove it. It is semantically misleading now that the creator is not auto-added. If kept, rename to clearly indicate it is just a convenience alias for `Participant.joined()`:

```kotlin
// REMOVE:
// fun creator(userId: UserId): Participant = Participant(userId, InviteStatus.JOINED)
```

`Participant.invited()` can also be cleaned up if the INVITED flow is not used.

### 9.4 -- InviteStatus.kt

```kotlin
enum class InviteStatus {
    JOINED,
    LEFT,
    // Consider removing: INVITED, KICKED (unused in current flows)
}
```

**DECISION REQUIRED [D-8]:** Remove `INVITED` and `KICKED` from `InviteStatus`?

Currently neither `INVITED` nor `KICKED` is used in any flow. Removing them simplifies the model. However, if invite-based joining is planned for later, keep `INVITED`.

### 9.5 -- ParticipantsRule.kt

```kotlin
object ParticipantsRule : CocroRule<Participant>() {
    const val MAX_ACTIVE_PARTICIPANTS = 4

    override fun isValid(values: List<Participant>): Boolean =
        countActiveParticipants(values) <= MAX_ACTIVE_PARTICIPANTS
        // CHANGED: removed `values.isNotEmpty()` -- an empty participants list is now valid
        // (session just created, nobody joined yet)

    fun canJoin(participants: List<Participant>): Boolean =
        countActiveParticipants(participants) < MAX_ACTIVE_PARTICIPANTS

    fun countActiveParticipants(participants: List<Participant>): Int =
        participants.count { it.status == InviteStatus.JOINED }
}
```

Key clarification: A participant with status `LEFT` does NOT count toward `MAX_ACTIVE_PARTICIPANTS`. This means:
- 4 users join (capacity full)
- 1 user leaves (3 active)
- A 5th user (or the left user) CAN join

---

## 10. Application Layer Impact (cocro-bff)

### 10.1 -- CreateSessionUseCase

**Changes:**
- Remove heartbeat registration
- Remove `sessionGridStateCache.get()` for response building
- Return `SessionCreationSuccess` instead of `SessionFullDto`
- Remove `ParticipantsRule.countActiveParticipants()` call (no participants)

The `SessionCreationSuccess` DTO already exists in `SessionSuccess.kt`.

### 10.2 -- JoinSessionUseCase

**Changes:**
- Handle rejoin-after-LEFT (currently rejected; now should succeed)
- On INTERRUPTED session join: status will flip to PLAYING in domain
- The rest remains largely the same

### 10.3 -- New: SynchroniseSessionUseCase

```kotlin
@Service
class SynchroniseSessionUseCase(
    private val currentUserProvider: CurrentUserProvider,
    private val sessionRepository: SessionRepository,
    private val sessionGridStateCache: SessionGridStateCache,
) {
    fun execute(shareCode: String): CocroResult<SessionFullDto, SessionError> {
        val user = currentUserProvider.currentUserOrNull()
            ?: return CocroResult.Error(listOf(SessionError.Unauthorized))

        val sessionShareCode = runCatching { SessionShareCode(shareCode) }.getOrElse {
            return CocroResult.Error(listOf(SessionError.InvalidShareCode(shareCode)))
        }

        val session = sessionRepository.findByShareCode(sessionShareCode)
            ?: return CocroResult.Error(listOf(SessionError.SessionNotFound(shareCode)))

        if (!session.participants.any { it.userId == user.userId && it.status == InviteStatus.JOINED }) {
            return CocroResult.Error(listOf(SessionError.UserNotParticipant(user.userId(), shareCode)))
        }

        val gridState = sessionGridStateCache.get(session.id) ?: session.sessionGridState

        // FLUSH
        sessionRepository.updateGridState(session.id, gridState)
        sessionGridStateCache.markFlushed(session.id, gridState.revision.value)

        val activeCount = ParticipantsRule.countActiveParticipants(session.participants)
        return CocroResult.Success(session.toSessionFullDto(gridState, activeCount))
    }
}
```

### 10.4 -- CheckGridUseCase (extended)

After the domain check, if `result.isComplete && result.isCorrect`:

```kotlin
// End-of-game transition
if (result.isComplete && result.isCorrect) {
    when (val endResult = session.endByCompletion()) {
        is CocroResult.Success -> {
            sessionRepository.save(endResult.value)
            sessionNotifier.broadcast(
                session.shareCode,
                SessionEvent.SessionEnded(
                    shareCode = session.shareCode.value,
                    correctCount = result.correctCount,
                    totalCount = result.totalCount,
                ),
            )
        }
        is CocroResult.Error -> {
            logger.warn("End-of-game transition failed: {}", endResult.errors)
            // Continue -- the check result is still returned
        }
    }
}
```

### 10.5 -- SessionEvent.kt (add new events)

```kotlin
@JsonTypeName("SessionEnded")
data class SessionEnded(
    val shareCode: String,
    val correctCount: Int,
    val totalCount: Int,
) : SessionEvent

@JsonTypeName("SessionInterrupted")  // if D-3 accepted
data class SessionInterrupted(
    val shareCode: String,
) : SessionEvent
```

### 10.6 -- SessionController.kt

Add new endpoint:

```kotlin
@PostMapping("/{shareCode}/sync")
@PreAuthorize("hasAnyRole('PLAYER', 'ADMIN', 'ANONYMOUS')")
fun synchroniseSession(@PathVariable shareCode: String): ResponseEntity<*> =
    synchroniseSessionUseCase.execute(shareCode).toResponseEntity(HttpStatus.OK)
```

Modify create response status:

```kotlin
@PostMapping
@PreAuthorize("hasAnyRole('PLAYER', 'ADMIN')")
fun createSession(@RequestBody dto: CreateSessionDto): ResponseEntity<*> =
    createSessionUseCase.execute(dto).toResponseEntity(HttpStatus.CREATED)
// Return type is now SessionCreationSuccess, not SessionFullDto
```

### 10.7 -- HeartbeatTimeoutScheduler

Add interrupt detection after processing timeouts:

```kotlin
// After all timed-out users are removed:
val activeCount = ParticipantsRule.countActiveParticipants(saved.participants)
if (activeCount == 0 && saved.status == SessionStatus.PLAYING) {
    val interrupted = saved.interrupt()
    sessionRepository.save(interrupted)
    sessionNotifier.broadcast(
        interrupted.shareCode,
        SessionEvent.SessionInterrupted(shareCode = interrupted.shareCode.value),
    )
}
```

### 10.8 -- LeaveSessionUseCase

Add interrupt detection after explicit leave:

```kotlin
// After saving the updated session:
val activeCount = ParticipantsRule.countActiveParticipants(updatedSession.participants)
if (activeCount == 0 && updatedSession.status == SessionStatus.PLAYING) {
    val interrupted = updatedSession.interrupt()
    sessionRepository.save(interrupted)
    sessionNotifier.broadcast(
        interrupted.shareCode,
        SessionEvent.SessionInterrupted(shareCode = interrupted.shareCode.value),
    )
}
```

---

## 11. Angular Frontend Impact

### 11.1 -- Domain Models

#### session.model.ts

```typescript
export type SessionStatus = 'PLAYING' | 'INTERRUPTED' | 'ENDED';
// Remove: 'CREATING' | 'SCORING'

// NEW response type for create
export interface SessionCreationResponse {
  sessionId: string;
  shareCode: string;
}
```

#### session-events.model.ts

```typescript
export type SessionEventType =
  | 'SessionWelcome'
  | 'ParticipantJoined'
  | 'ParticipantLeft'
  | 'GridUpdated'
  | 'GridChecked'
  | 'SyncRequired'
  | 'SessionEnded'        // NEW
  | 'SessionInterrupted'; // NEW (if D-3)

export interface SessionEndedEvent extends SessionEvent {
  type: 'SessionEnded';
  shareCode: string;
  correctCount: number;
  totalCount: number;
}

export interface SessionInterruptedEvent extends SessionEvent {
  type: 'SessionInterrupted';
  shareCode: string;
}
```

### 11.2 -- GameSessionPort

```typescript
export interface GameSessionPort {
  createSession(dto: CreateSessionRequest): Observable<SessionCreationResponse>;  // CHANGED
  joinSession(dto: JoinSessionRequest): Observable<SessionFullResponse>;
  leaveSession(dto: LeaveSessionRequest): Observable<SessionLeaveResponse>;
  getState(shareCode: string): Observable<SessionStateResponse>;
  syncSession(shareCode: string): Observable<SessionFullResponse>;  // NEW
}
```

### 11.3 -- Angular Use Cases

#### CreateSessionUseCase (NEW -- currently not a standalone use case)

```typescript
@Injectable({ providedIn: 'root' })
export class CreateSessionUseCase {
  constructor(
    @Inject(GAME_SESSION_PORT) private sessionPort: GameSessionPort,
    private router: Router,
  ) {}

  execute(gridId: string): void {
    this.sessionPort.createSession({ gridId }).subscribe({
      next: (response) => {
        this.router.navigate(['/play', response.shareCode]);
        // JoinSession will be called by GridPlayerComponent.ngOnInit()
      },
    });
  }
}
```

#### SyncSessionUseCase (NEW)

```typescript
@Injectable({ providedIn: 'root' })
export class SyncSessionUseCase {
  constructor(
    @Inject(GAME_SESSION_PORT) private sessionPort: GameSessionPort,
  ) {}

  execute(shareCode: string): Observable<SessionFullResponse> {
    return this.sessionPort.syncSession(shareCode);
  }
}
```

### 11.4 -- GridPlayerComponent Changes

#### Initialization (no change in flow)

`ngOnInit()` already calls `joinSession` then `connect`. This flow is preserved. The only change is that create no longer returns a full DTO -- the component still gets its full DTO from join.

#### Add SessionEnded handling

```typescript
case 'SessionEnded':
  this.status.set('ENDED');
  // Disable input, show completion UI
  break;
case 'SessionInterrupted':
  this.status.set('INTERRUPTED');
  // Show "session interrupted" message
  break;
```

#### Change default status

```typescript
readonly status = signal<SessionStatus>('PLAYING');  // was 'CREATING'
```

#### Leave button

Already implemented: `leave()` method exists on the component. The Leave button is referenced in the template. Currently calls `leaveSession` then navigates to `/`.

#### Sync handler update

Change `resync()` to use the new `syncSession` endpoint instead of `getState`:

```typescript
private resync(_targetRevision: number): void {
    this.letterAuthors.set(new Map());
    this.gameSession.syncSession(this.shareCode()).subscribe({
      next: (fullDto: SessionFullResponse) => {
        this.revision.set(fullDto.gridRevision);
        this.status.set(fullDto.status);
        this.participantCount.set(fullDto.participantCount);
        fullDto.cells.forEach((c: CellStateDto) => {
          if (c.letter) this.selector.setLetterAt(c.x, c.y, c.letter);
        });
      },
    });
}
```

---

## 12. Business Rules Summary

| Rule | Description | Current | After |
|------|-------------|---------|-------|
| BR-1 | Max active participants | 4 (JOINED only) | Unchanged |
| BR-2 | LEFT participant counts toward capacity | No | No (unchanged) |
| BR-3 | Creator auto-joined at creation | Yes | **No** |
| BR-4 | Creator must be in participants for rehydrate | Yes | **No** |
| BR-5 | Only creator can end session | Yes (Session.end) | Yes for manual end, **No for completion-end** |
| BR-6 | Join allowed on CREATING | Yes | **No** (status removed) |
| BR-7 | Join allowed on PLAYING | Yes | Yes |
| BR-8 | Join allowed on INTERRUPTED | No | **Yes** (resumes session, see D-2) |
| BR-9 | Rejoin after LEFT | Rejected (AlreadyParticipant) | **Allowed** (flips status to JOINED) |
| BR-10 | CheckGrid triggers end on complete+correct | No | **Yes** |
| BR-11 | Idempotent join returns full DTO | Yes | Yes (confirmed) |
| BR-12 | Last participant leaves -> INTERRUPTED | Implicit (no event) | **Explicit** (SessionInterrupted event, see D-3) |

---

## 13. Decisions Required

| ID | Question | Options | Recommendation |
|----|----------|---------|----------------|
| D-1 | How should rejoin-after-LEFT work in the domain? | (A) Flip existing LEFT entry to JOINED in-place, (B) Remove old entry + add new | **A** -- in-place flip is cleaner and preserves participant history |
| D-2 | Can a user join an INTERRUPTED session? | (A) No -- frozen, (B) Yes -- resumes to PLAYING | **B** -- natural recovery when someone comes back |
| D-3 | Should there be a SessionInterrupted WebSocket event? | (A) No -- silent, (B) Yes -- explicit broadcast | **B** -- client needs to know to disable input |
| D-4 | Special behavior when creator leaves? | (A) No special treatment, (B) Transfer creator role, (C) End session | **A** -- keep it simple |
| D-5 | HTTP method for /sync endpoint? | GET or POST | **POST** -- has flush side effect |
| D-6 | Who calls Session.end() in CheckGrid? | (A) New endByCompletion() (no actor check), (B) Remove isCreator from end(), (C) Use creatorId as actor | **A** -- clean separation of concerns |
| D-7 | Can CheckGrid be called on INTERRUPTED session? | (A) No, (B) Yes | **A** -- INTERRUPTED = no active participants |
| D-8 | Remove INVITED and KICKED from InviteStatus? | (A) Yes -- simplify, (B) No -- keep for future | **B** -- low cost to keep, avoids premature removal |

---

## 14. Migration / Backward Compatibility

### Database (MongoDB)

Existing sessions in the database may have:
- `status: "CREATING"` or `status: "SCORING"`
- Participants list including the creator

**Migration strategy:**
1. Sessions with `status: "CREATING"` -> update to `"PLAYING"`
2. Sessions with `status: "SCORING"` -> update to `"PLAYING"` (or `"ENDED"` if the scoring result was persisted)
3. No change needed for participant lists -- they remain valid, the `rehydrate()` invariant change simply relaxes the constraint

### API Contract

The `POST /api/sessions` response body changes from `SessionFullDto` to `SessionCreationSuccess`. This is a **breaking change** for any client consuming the create response.

**Angular impact:** The create use case must be updated to handle the new response type. Since the Angular `CreateSessionUseCase` does not currently exist as a standalone class (creation is done inline), this is a minor change.

### WebSocket Events

New event types (`SessionEnded`, `SessionInterrupted`) are additive. Existing clients that do not handle them will simply ignore them (unknown event types fall through the switch statement). No breaking change.

### Tests

| Test file | Required changes |
|-----------|-----------------|
| `SessionCreateTest` | Update: creator is no longer auto-joined. `participants` should be empty. |
| `SessionApplyTest.Join` | Remove CREATING test. Add INTERRUPTED test (join resumes). Add rejoin-after-LEFT test. |
| `SessionApplyTest.Leave` | No change. |
| `SessionEndTest` | Remove CREATING and SCORING tests. Add `endByCompletion()` tests. |
| `ParticipantsRuleTest` | Update `isValid` to accept empty list. |
| `SessionLifecycleIT` (integration) | Update expectations for create response type. |
| `SessionWebSocketIT` (integration) | Add SessionEnded event assertion. |

---

*End of specification.*
