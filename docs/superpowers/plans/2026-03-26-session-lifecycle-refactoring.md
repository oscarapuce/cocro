# Session Lifecycle Refactoring — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Simplify the session lifecycle to 3 states (PLAYING/INTERRUPTED/ENDED), decouple creation from joining, add SynchroniseSession, and wire end-of-game via CheckGrid.

**Architecture:** cocro-shared domain is absorbed into cocro-bff (single module). Session creation returns a lightweight `{sessionId, shareCode}`. JoinSession is the single entry point for `SessionFullDto`. CheckGrid triggers `Session.end()` when complete + correct.

**Tech Stack:** Kotlin/Spring Boot 3.2, MongoDB 7, Redis, STOMP WebSocket, Angular 20 Signals, Mockito-Kotlin, Testcontainers

**Spec:** `docs/specs/session-lifecycle-refactoring-spec.md`

---

## Phase 1 — Module consolidation

### Task 1: Absorb cocro-shared into cocro-bff

**Files:**
- Modify: `settings.gradle.kts`
- Modify: `cocro-bff/build.gradle.kts`
- Move all: `cocro-shared/src/commonMain/kotlin/` → `cocro-bff/src/main/kotlin/`
- Move all: `cocro-shared/src/jvmTest/kotlin/` → `cocro-bff/src/test/kotlin/`

- [ ] **Step 1: Move domain source files**

```bash
cp -r cocro-shared/src/commonMain/kotlin/com cocro-bff/src/main/kotlin/
cp -r cocro-shared/src/jvmTest/kotlin/com cocro-bff/src/test/kotlin/
```

Verify the copy:
```bash
ls cocro-bff/src/main/kotlin/com/cocro/kernel/session/
ls cocro-bff/src/test/kotlin/com/cocro/kernel/session/
```

- [ ] **Step 2: Remove cocro-shared dependency from cocro-bff/build.gradle.kts**

In `cocro-bff/build.gradle.kts`, remove:
```kotlin
// DELETE this line:
implementation(project(":cocro-shared"))
```

Also add test dependencies (JUnit Jupiter + AssertJ — already provided by spring-boot-starter-test for JUnit, but AssertJ needs verifying):
```kotlin
// AssertJ is already included via spring-boot-starter-test
// No change needed
```

- [ ] **Step 3: Remove :cocro-shared (and :cocro-cmp) from settings.gradle.kts**

```kotlin
// BEFORE
include(
    ":cocro-shared",
    ":cocro-bff",
    ":cocro-cmp",
)

// AFTER
include(":cocro-bff")
```

- [ ] **Step 4: Verify build compiles**

```bash
./gradlew cocro-bff:compileKotlin
```

Expected: BUILD SUCCESSFUL (no unresolved references)

- [ ] **Step 5: Run existing tests to establish baseline**

```bash
TESTCONTAINERS_RYUK_DISABLED=true ./gradlew cocro-bff:test --no-daemon 2>&1 | tail -20
```

Expected: most tests pass (some may fail due to moved test files — that is OK at this stage)

- [ ] **Step 6: Commit**

```bash
git add cocro-bff/src/main/kotlin/com/cocro/kernel \
        cocro-bff/src/test/kotlin/com/cocro/kernel \
        cocro-bff/build.gradle.kts \
        settings.gradle.kts
git commit -m "chore: absorb cocro-shared domain into cocro-bff"
```

---

## Phase 2 — Domain model changes

### Task 2: Simplify SessionStatus to 3 states

**Files:**
- Modify: `cocro-bff/src/main/kotlin/com/cocro/kernel/session/enum/SessionStatus.kt`
- Modify: `cocro-bff/src/test/kotlin/com/cocro/kernel/session/model/SessionEndTest.kt`
- Modify: `cocro-bff/src/test/kotlin/com/cocro/kernel/session/model/SessionApplyTest.kt`
- Modify: `cocro-bff/src/test/kotlin/com/cocro/kernel/session/model/SessionCreateTest.kt`

- [ ] **Step 1: Update SessionStatus enum**

Replace entire content of `cocro-bff/src/main/kotlin/com/cocro/kernel/session/enum/SessionStatus.kt`:

```kotlin
package com.cocro.kernel.session.enum

enum class SessionStatus {
    PLAYING,
    INTERRUPTED,
    ENDED,
}
```

- [ ] **Step 2: Run compile to find all CREATING/SCORING references**

```bash
./gradlew cocro-bff:compileKotlin 2>&1 | grep -E "error:|Unresolved|CREATING|SCORING"
```

Expected: compile errors in Session.kt (applyJoin, end), SessionEndTest.kt, SessionApplyTest.kt, possibly SessionDocumentMapper.kt

- [ ] **Step 3: Fix Session.kt references to CREATING/SCORING**

In `cocro-bff/src/main/kotlin/com/cocro/kernel/session/model/Session.kt`:

`applyJoin()` — replace status guard:
```kotlin
// BEFORE
if (status !in setOf(SessionStatus.CREATING, SessionStatus.PLAYING)) {

// AFTER
if (status !in setOf(SessionStatus.PLAYING, SessionStatus.INTERRUPTED)) {
```

`end()` — replace status guard (keep for now, will update fully in Task 4):
```kotlin
// BEFORE
if (status !in setOf(SessionStatus.CREATING, SessionStatus.PLAYING, SessionStatus.SCORING)) {

// AFTER
if (status != SessionStatus.PLAYING) {
```

- [ ] **Step 4: Fix SessionEndTest — remove CREATING test, update signatures**

In `cocro-bff/src/test/kotlin/com/cocro/kernel/session/model/SessionEndTest.kt`:

Remove the test `creator can end a CREATING session` (entire test method).

Keep and verify the remaining tests compile. They still use `session.end(creatorId)` — this will break in Task 4 when we remove actorId, but for now just fix the CREATING/SCORING compilation errors.

- [ ] **Step 5: Fix SessionApplyTest — remove CREATING test**

In `cocro-bff/src/test/kotlin/com/cocro/kernel/session/model/SessionApplyTest.kt`:

Remove the test `should add participant when session is CREATING` (lines 42–51).

- [ ] **Step 5b: Fix CheckGridUseCaseTest — replace CREATING with ENDED**

The existing test `should return InvalidStatusForAction when session is not PLAYING` in
`cocro-bff/src/test/kotlin/com/cocro/application/session/usecase/CheckGridUseCaseTest.kt`
constructs a session with `status = SessionStatus.CREATING`. Replace it:

```kotlin
// BEFORE
status = SessionStatus.CREATING,

// AFTER
status = SessionStatus.ENDED,
```

The assertion (`InvalidStatusForAction` error) stays unchanged — an ENDED session is also not PLAYING.

- [ ] **Step 6: Run tests to verify no new failures**

```bash
TESTCONTAINERS_RYUK_DISABLED=true ./gradlew cocro-bff:test --tests "com.cocro.kernel.*" --no-daemon 2>&1 | tail -20
```

- [ ] **Step 7: Commit**

```bash
git add cocro-bff/src/main/kotlin/com/cocro/kernel/session/enum/SessionStatus.kt \
        cocro-bff/src/main/kotlin/com/cocro/kernel/session/model/Session.kt \
        cocro-bff/src/test/kotlin/com/cocro/kernel/session/model/
git commit -m "feat(domain): reduce SessionStatus to PLAYING/INTERRUPTED/ENDED"
```

---

### Task 3: Session.create() — empty participants

**Files:**
- Modify: `cocro-bff/src/main/kotlin/com/cocro/kernel/session/model/Session.kt`
- Modify: `cocro-bff/src/test/kotlin/com/cocro/kernel/session/model/SessionCreateTest.kt`
- Modify: `cocro-bff/src/test/kotlin/com/cocro/kernel/session/model/SessionApplyTest.kt`

- [ ] **Step 1: Update SessionCreateTest — invert creator test, update session-full test**

Replace `Session creator is auto-joined on create` with:
```kotlin
@Test
fun `Session create produces empty participants list`() {
    val session = Session.create(
        creatorId = UserId.new(),
        shareCode = SessionShareCode("AB12"),
        gridId = GridShareCode("GRID01"),
        gridTemplate = minimalSnapshot(),
    )
    assertThat(session.participants).isEmpty()
}
```

- [ ] **Step 2: Run test to verify it FAILS (creator is still auto-joined)**

```bash
./gradlew cocro-bff:test --tests "com.cocro.kernel.session.model.SessionCreateTest.Session create produces empty participants list" --no-daemon 2>&1 | tail -10
```

Expected: FAIL — participants has 1 element (creator)

- [ ] **Step 3: Update Session.create() — empty participants**

In `cocro-bff/src/main/kotlin/com/cocro/kernel/session/model/Session.kt`, in `create()`:
```kotlin
// BEFORE
participants = listOf(Participant.creator(creatorId)),

// AFTER
participants = emptyList(),
```

- [ ] **Step 4: Remove creator-must-be-participant invariant from rehydrate()**

In the same file, `rehydrate()` — remove these lines:
```kotlin
// DELETE:
require(participants.any { it.userId == creatorId }) {
    "Creator must be participant"
}
```

- [ ] **Step 5: Run SessionCreateTest**

```bash
./gradlew cocro-bff:test --tests "com.cocro.kernel.session.model.SessionCreateTest" --no-daemon 2>&1 | tail -15
```

Expected: all pass

- [ ] **Step 6: Fix SessionApplyTest — update already-participant and full-session tests**

The `session` in SessionApplyTest is now empty (no creator). Update:

`should fail when user is already a participant` → user must first be joined:
```kotlin
@Test
fun `should fail when user is already a JOINED participant`() {
    val joiner = UserId.new()
    val sessionWithJoiner = session.join(joiner)

    val result = sessionWithJoiner.apply(SessionLifecycleCommand.Join(joiner))

    assertThat(result).isInstanceOf(CocroResult.Error::class.java)
    val errors = (result as CocroResult.Error).errors
    assertThat(errors).anyMatch { it is SessionError.AlreadyParticipant }
}
```

`should fail when session is full` → session starts empty, need 4 joins (not 3):
```kotlin
@Test
fun `should fail when session is full`() {
    val fullSession = (1..4).fold(session) { s, _ -> s.join(UserId.new()) }
    val joiner = UserId.new()

    val result = fullSession.apply(SessionLifecycleCommand.Join(joiner))

    assertThat(result).isInstanceOf(CocroResult.Error::class.java)
    val errors = (result as CocroResult.Error).errors
    assertThat(errors).contains(SessionError.SessionFull)
}
```

- [ ] **Step 7: Run all domain tests**

```bash
./gradlew cocro-bff:test --tests "com.cocro.kernel.*" --no-daemon 2>&1 | tail -20
```

Expected: all pass

- [ ] **Step 8: Update ParticipantsRule.isValid() — accept empty list**

In `cocro-bff/src/main/kotlin/com/cocro/kernel/session/rule/ParticipantsRule.kt`:
```kotlin
// BEFORE: likely has values.isNotEmpty() &&
// AFTER: remove the isNotEmpty() check
override fun isValid(values: List<Participant>): Boolean =
    countActiveParticipants(values) <= MAX_ACTIVE_PARTICIPANTS
```

Check `ParticipantsRuleTest.kt` in `cocro-bff/src/test/kotlin/com/cocro/kernel/session/rule/` and update if it has a test asserting empty list is invalid.

- [ ] **Step 9: Commit**

```bash
git add cocro-bff/src/main/kotlin/com/cocro/kernel/session/model/Session.kt \
        cocro-bff/src/main/kotlin/com/cocro/kernel/session/rule/ParticipantsRule.kt \
        cocro-bff/src/test/kotlin/com/cocro/kernel/session/model/ \
        cocro-bff/src/test/kotlin/com/cocro/kernel/session/rule/
git commit -m "feat(domain): Session.create() produces empty participants list"
```

---

### Task 4: Session.end() — remove actorId, system-level transition

**Files:**
- Modify: `cocro-bff/src/main/kotlin/com/cocro/kernel/session/model/Session.kt`
- Modify: `cocro-bff/src/test/kotlin/com/cocro/kernel/session/model/SessionEndTest.kt`

- [ ] **Step 1: Update SessionEndTest — remove non-creator test, update signatures**

Replace entire `SessionEndTest.kt`:
```kotlin
package com.cocro.kernel.session.model

import com.cocro.kernel.common.CocroResult
import com.cocro.kernel.grid.model.GridTemplateSnapshot
import com.cocro.kernel.grid.model.valueobject.GridShareCode
import com.cocro.kernel.session.enum.SessionStatus
import com.cocro.kernel.session.error.SessionError
import com.cocro.kernel.session.model.valueobject.SessionShareCode
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import com.cocro.kernel.auth.model.valueobject.UserId

class SessionEndTest {

    private val session = Session.create(
        creatorId = UserId.new(),
        shareCode = SessionShareCode("AB12"),
        gridId = GridShareCode("GRID01"),
        gridTemplate = GridTemplateSnapshot(
            shortId = GridShareCode("GRID01"),
            title = "Test", width = 5, height = 5,
            difficulty = null, author = null, reference = null,
            description = null, globalClueLabel = null,
            globalClueWordLengths = null, cells = emptyList()
        ),
    )

    private fun Session.withStatus(status: SessionStatus): Session =
        Session.rehydrate(id, shareCode, creatorId, gridId, status, participants, sessionGridState, createdAt, updatedAt, gridTemplate = gridTemplate)

    @Test
    fun `can end a PLAYING session`() {
        val result = session.end()

        assertThat(result).isInstanceOf(CocroResult.Success::class.java)
        assertThat((result as CocroResult.Success).value.status).isEqualTo(SessionStatus.ENDED)
    }

    @Test
    fun `cannot end an already ENDED session`() {
        val ended = session.withStatus(SessionStatus.ENDED)

        val result = ended.end()

        assertThat(result).isInstanceOf(CocroResult.Error::class.java)
        assertThat((result as CocroResult.Error).errors).anyMatch { it is SessionError.InvalidStatusForAction }
    }

    @Test
    fun `cannot end an INTERRUPTED session`() {
        val interrupted = session.withStatus(SessionStatus.INTERRUPTED)

        val result = interrupted.end()

        assertThat(result).isInstanceOf(CocroResult.Error::class.java)
        assertThat((result as CocroResult.Error).errors).anyMatch { it is SessionError.InvalidStatusForAction }
    }
}
```

- [ ] **Step 2: Run test to verify it FAILS (end() still requires actorId)**

```bash
./gradlew cocro-bff:test --tests "com.cocro.kernel.session.model.SessionEndTest" --no-daemon 2>&1 | tail -10
```

Expected: compile error — `end()` requires actorId parameter

- [ ] **Step 3: Update Session.end() — remove actorId**

In `cocro-bff/src/main/kotlin/com/cocro/kernel/session/model/Session.kt`:
```kotlin
// BEFORE
fun end(
    actorId: UserId,
    now: Instant = Instant.now(),
): CocroResult<Session, SessionError> {
    if (!isCreator(actorId)) {
        return err(SessionError.NotCreator(creatorId.toString(), actorId.toString()))
    }
    if (status !in setOf(SessionStatus.CREATING, SessionStatus.PLAYING, SessionStatus.SCORING)) {
        return err(SessionError.InvalidStatusForAction(status, "end"))
    }
    return ok(copy(status = SessionStatus.ENDED, updatedAt = now))
}

// AFTER
fun end(now: Instant = Instant.now()): CocroResult<Session, SessionError> {
    if (status != SessionStatus.PLAYING) {
        return err(SessionError.InvalidStatusForAction(status, "end"))
    }
    return ok(copy(status = SessionStatus.ENDED, updatedAt = now))
}
```

Also remove `private fun isCreator(actorId: UserId)` if no longer used anywhere. Check by running compile.

- [ ] **Step 4: Run SessionEndTest**

```bash
./gradlew cocro-bff:test --tests "com.cocro.kernel.session.model.SessionEndTest" --no-daemon 2>&1 | tail -10
```

Expected: all 3 tests pass

- [ ] **Step 5: Commit**

```bash
git add cocro-bff/src/main/kotlin/com/cocro/kernel/session/model/Session.kt \
        cocro-bff/src/test/kotlin/com/cocro/kernel/session/model/SessionEndTest.kt
git commit -m "feat(domain): Session.end() is actor-free system transition"
```

---

### Task 5: Session.applyJoin() + Session.join() — INTERRUPTED + LEFT rejoin

**Files:**
- Modify: `cocro-bff/src/main/kotlin/com/cocro/kernel/session/model/Session.kt`
- Modify: `cocro-bff/src/test/kotlin/com/cocro/kernel/session/model/SessionApplyTest.kt`

- [ ] **Step 1: Add new failing tests for INTERRUPTED join and LEFT rejoin**

In `SessionApplyTest.kt`, inside the `Join` nested class, add:
```kotlin
@Test
fun `should allow join on INTERRUPTED session and resume to PLAYING`() {
    val interrupted = session.withStatus(SessionStatus.INTERRUPTED)
    val joiner = UserId.new()

    val result = interrupted.apply(SessionLifecycleCommand.Join(joiner))

    assertThat(result).isInstanceOf(CocroResult.Success::class.java)
    val updated = (result as CocroResult.Success).value
    assertThat(updated.status).isEqualTo(SessionStatus.PLAYING)
    assertThat(updated.participants).anyMatch { it.userId == joiner && it.status == InviteStatus.JOINED }
}

@Test
fun `should allow user with LEFT status to rejoin (flip in-place)`() {
    val joiner = UserId.new()
    val sessionAfterLeave = session.join(joiner).leave(joiner)

    val result = sessionAfterLeave.apply(SessionLifecycleCommand.Join(joiner))

    assertThat(result).isInstanceOf(CocroResult.Success::class.java)
    val updated = (result as CocroResult.Success).value
    // In-place flip: exactly one entry for joiner, status JOINED
    assertThat(updated.participants.filter { it.userId == joiner }).hasSize(1)
    assertThat(updated.participants).anyMatch { it.userId == joiner && it.status == InviteStatus.JOINED }
}

@Test
fun `LEFT participant does not count toward capacity`() {
    // Fill with 4, then one leaves — 5th can join
    val ids = (1..4).map { UserId.new() }
    val filled = ids.fold(session) { s, id -> s.join(id) }
    val afterLeave = filled.leave(ids[0])

    val joiner = UserId.new()
    val result = afterLeave.apply(SessionLifecycleCommand.Join(joiner))

    assertThat(result).isInstanceOf(CocroResult.Success::class.java)
}
```

Also, invert the existing `should fail when session is INTERRUPTED` test to pass now:
```kotlin
// DELETE the old "should fail when session is INTERRUPTED" test (it's replaced by the new one above)
```

- [ ] **Step 2: Run new tests to verify they FAIL**

```bash
./gradlew cocro-bff:test --tests "com.cocro.kernel.session.model.SessionApplyTest" --no-daemon 2>&1 | tail -20
```

Expected: 3 new tests FAIL

- [ ] **Step 3: Update Session.applyJoin() — INTERRUPTED + check JOINED only**

In `cocro-bff/src/main/kotlin/com/cocro/kernel/session/model/Session.kt`:
```kotlin
private fun applyJoin(actorId: UserId): CocroResult<Session, SessionError> {
    if (status !in setOf(SessionStatus.PLAYING, SessionStatus.INTERRUPTED)) {
        return err(SessionError.InvalidStatusForAction(status, "join"))
    }
    // Only JOINED participants block a re-join; LEFT participants may rejoin
    if (participants.any { it.userId == actorId && it.status == InviteStatus.JOINED }) {
        return err(SessionError.AlreadyParticipant(actorId.toString(), shareCode.value))
    }
    // LEFT participant does not count toward capacity
    val isRejoin = participants.any { it.userId == actorId && it.status == InviteStatus.LEFT }
    if (!isRejoin && !ParticipantsRule.canJoin(participants)) {
        return err(SessionError.SessionFull)
    }
    val updated = join(actorId)
    // Resume INTERRUPTED → PLAYING when first participant joins
    val resumed = if (status == SessionStatus.INTERRUPTED) updated.copy(status = SessionStatus.PLAYING) else updated
    return ok(resumed)
}
```

- [ ] **Step 4: Update Session.join() — flip LEFT→JOINED in-place**

```kotlin
fun join(
    actorId: UserId,
    now: Instant = Instant.now(),
): Session {
    val leftIndex = participants.indexOfFirst { it.userId == actorId && it.status == InviteStatus.LEFT }
    val updatedParticipants = if (leftIndex >= 0) {
        participants.mapIndexed { i, p -> if (i == leftIndex) p.copy(status = InviteStatus.JOINED) else p }
    } else {
        participants + Participant.joined(actorId)
    }
    return copy(participants = updatedParticipants, updatedAt = now)
}
```

- [ ] **Step 5: Run all SessionApplyTest**

```bash
./gradlew cocro-bff:test --tests "com.cocro.kernel.session.model.SessionApplyTest" --no-daemon 2>&1 | tail -20
```

Expected: all tests pass

- [ ] **Step 6: Commit**

```bash
git add cocro-bff/src/main/kotlin/com/cocro/kernel/session/model/Session.kt \
        cocro-bff/src/test/kotlin/com/cocro/kernel/session/model/SessionApplyTest.kt
git commit -m "feat(domain): join accepts INTERRUPTED + allows rejoin after LEFT"
```

---

### Task 6: Cleanup — Participant.creator() / Participant.invited()

**Files:**
- Modify: `cocro-bff/src/main/kotlin/com/cocro/kernel/session/Participant.kt`

- [ ] **Step 1: Remove unused factory methods**

Replace `cocro-bff/src/main/kotlin/com/cocro/kernel/session/Participant.kt`:
```kotlin
package com.cocro.kernel.session

import com.cocro.kernel.auth.model.valueobject.UserId
import com.cocro.kernel.session.enum.InviteStatus

data class Participant(
    val userId: UserId,
    val status: InviteStatus,
) {
    companion object {
        fun joined(userId: UserId): Participant = Participant(userId, InviteStatus.JOINED)
    }
}
```

- [ ] **Step 2: Verify compile (no remaining callers of creator() or invited())**

```bash
./gradlew cocro-bff:compileKotlin 2>&1 | grep -E "error:|Unresolved"
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Run all domain tests**

```bash
./gradlew cocro-bff:test --tests "com.cocro.kernel.*" --no-daemon 2>&1 | tail -10
```

Expected: all pass

- [ ] **Step 4: Commit**

```bash
git add cocro-bff/src/main/kotlin/com/cocro/kernel/session/Participant.kt
git commit -m "chore(domain): remove obsolete Participant.creator() and Participant.invited()"
```

---

## Phase 3 — BFF application layer

### Task 7: SessionEvent — add SessionEnded + SessionInterrupted

**Files:**
- Modify: `cocro-bff/src/main/kotlin/com/cocro/application/session/dto/notification/SessionEvent.kt`

- [ ] **Step 1: Add two new event types**

In `SessionEvent.kt`, add to `@JsonSubTypes`:
```kotlin
JsonSubTypes.Type(SessionEvent.SessionEnded::class, name = "SessionEnded"),
JsonSubTypes.Type(SessionEvent.SessionInterrupted::class, name = "SessionInterrupted"),
```

Add inside the sealed interface:
```kotlin
@JsonTypeName("SessionEnded")
data class SessionEnded(
    val shareCode: String,
    val correctCount: Int,
    val totalCount: Int,
) : SessionEvent

@JsonTypeName("SessionInterrupted")
data class SessionInterrupted(
    val shareCode: String,
) : SessionEvent
```

- [ ] **Step 2: Verify compile**

```bash
./gradlew cocro-bff:compileKotlin 2>&1 | grep -E "error:"
```

- [ ] **Step 3: Commit**

```bash
git add cocro-bff/src/main/kotlin/com/cocro/application/session/dto/notification/SessionEvent.kt
git commit -m "feat(bff): add SessionEnded and SessionInterrupted WS events"
```

---

### Task 8: CreateSessionUseCase — return SessionCreationSuccess

**Files:**
- Modify: `cocro-bff/src/main/kotlin/com/cocro/application/session/usecase/CreateSessionUseCase.kt`
- Modify: `cocro-bff/src/test/kotlin/com/cocro/application/session/usecase/CreateSessionUseCaseTest.kt` (if it exists, else create)
- Modify: `cocro-bff/src/main/kotlin/com/cocro/presentation/rest/session/SessionController.kt`

- [ ] **Step 1: Check if CreateSessionUseCaseTest exists**

```bash
ls cocro-bff/src/test/kotlin/com/cocro/application/session/usecase/
```

If it doesn't exist, create it with the following content. If it exists, update it.

- [ ] **Step 2: Write/update unit test**

Create or update `cocro-bff/src/test/kotlin/com/cocro/application/session/usecase/CreateSessionUseCaseTest.kt`:
```kotlin
package com.cocro.application.session.usecase

import com.cocro.application.auth.port.CurrentUserProvider
import com.cocro.application.grid.port.GridRepository
import com.cocro.application.session.dto.SessionCreationSuccess
import com.cocro.application.session.port.HeartbeatTracker
import com.cocro.application.session.port.SessionGridStateCache
import com.cocro.application.session.port.SessionRepository
import com.cocro.application.session.service.SessionCodeGenerator
import com.cocro.kernel.auth.enum.Role
import com.cocro.kernel.auth.model.AuthenticatedUser
import com.cocro.kernel.auth.model.valueobject.UserId
import com.cocro.kernel.common.CocroResult
import com.cocro.kernel.grid.model.Grid
import com.cocro.kernel.grid.model.valueobject.GridShareCode
import com.cocro.kernel.session.model.Session
import com.cocro.kernel.session.model.valueobject.SessionShareCode
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class CreateSessionUseCaseTest {

    private val currentUserProvider: CurrentUserProvider = mock()
    private val sessionRepository: SessionRepository = mock()
    private val sessionGridStateCache: SessionGridStateCache = mock()
    private val shareCodeGenerator: SessionCodeGenerator = mock()
    private val gridRepository: GridRepository = mock()
    // HeartbeatTracker must NOT be injected in CreateSessionUseCase after this task

    private val useCase = CreateSessionUseCase(
        currentUserProvider, sessionRepository, sessionGridStateCache,
        shareCodeGenerator, gridRepository,
    )

    @Test
    fun `should return SessionCreationSuccess with sessionId and shareCode`() {
        val creatorId = UserId.new()
        val creator = AuthenticatedUser(creatorId, setOf(Role.PLAYER))
        val shareCode = SessionShareCode("AB12")
        val gridId = GridShareCode("GRID01")
        // Build a minimal Grid mock — use mock() to avoid constructing the full domain object
        val grid: Grid = mock()
        whenever(currentUserProvider.currentUserOrNull()).thenReturn(creator)
        whenever(shareCodeGenerator.generateId()).thenReturn(shareCode)
        whenever(gridRepository.findByShortId(gridId)).thenReturn(grid)
        whenever(grid.toGridTemplateSnapshot()).thenCallRealMethod() // or just mock
        whenever(sessionRepository.save(any())).thenAnswer { it.arguments[0] as Session }

        val result = useCase.execute(com.cocro.application.session.dto.CreateSessionDto(gridId = "GRID01"))

        assertThat(result).isInstanceOf(CocroResult.Success::class.java)
        val success = (result as CocroResult.Success).value
        assertThat(success).isInstanceOf(SessionCreationSuccess::class.java)
        assertThat(success.shareCode).isEqualTo("AB12")
    }

    @Test
    fun `should NOT register heartbeat on create`() {
        // HeartbeatTracker is no longer injected — this test documents the absence
        // Verify by checking the constructor of CreateSessionUseCase has no HeartbeatTracker param
        val constructor = CreateSessionUseCase::class.java.constructors[0]
        val paramTypes = constructor.parameterTypes.map { it.simpleName }
        assertThat(paramTypes).doesNotContain("HeartbeatTracker")
    }

    @Test
    fun `should return Unauthorized when user not authenticated`() {
        whenever(currentUserProvider.currentUserOrNull()).thenReturn(null)

        val result = useCase.execute(com.cocro.application.session.dto.CreateSessionDto(gridId = "GRID01"))

        assertThat(result).isInstanceOf(CocroResult.Error::class.java)
        verifyNoInteractions(sessionRepository)
    }
}
```

- [ ] **Step 3: Update CreateSessionUseCase**

Replace `cocro-bff/src/main/kotlin/com/cocro/application/session/usecase/CreateSessionUseCase.kt`:
```kotlin
package com.cocro.application.session.usecase

import com.cocro.application.auth.port.CurrentUserProvider
import com.cocro.application.grid.port.GridRepository
import com.cocro.application.session.dto.CreateSessionDto
import com.cocro.application.session.dto.SessionCreationSuccess
import com.cocro.application.session.mapper.toGridTemplateSnapshot
import com.cocro.application.session.mapper.toSessionCreationSuccess
import com.cocro.application.session.port.SessionGridStateCache
import com.cocro.application.session.port.SessionRepository
import com.cocro.application.session.service.SessionCodeGenerator
import com.cocro.application.session.validation.validateCreateSessionDto
import com.cocro.kernel.common.CocroResult
import com.cocro.kernel.grid.model.valueobject.GridShareCode
import com.cocro.kernel.session.error.SessionError
import com.cocro.kernel.session.model.Session
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class CreateSessionUseCase(
    private val currentUserProvider: CurrentUserProvider,
    private val sessionRepository: SessionRepository,
    private val sessionGridStateCache: SessionGridStateCache,
    private val shareCodeGenerator: SessionCodeGenerator,
    private val gridRepository: GridRepository,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun execute(createSessionDto: CreateSessionDto): CocroResult<SessionCreationSuccess, SessionError> {
        val user = currentUserProvider.currentUserOrNull()
            ?: run {
                logger.warn("Session creation rejected: user not authenticated")
                return CocroResult.Error(listOf(SessionError.Unauthorized))
            }

        val errors = validateCreateSessionDto(createSessionDto)
        if (errors.isNotEmpty()) {
            logger.warn("Session creation rejected: {} validation errors for gridId={}", errors.size, createSessionDto.gridId)
            return CocroResult.Error(errors)
        }

        val gridId = GridShareCode(createSessionDto.gridId)
        val grid = gridRepository.findByShortId(gridId)
            ?: run {
                logger.warn("Session creation rejected: grid not found with id={}", gridId.value)
                return CocroResult.Error(listOf(SessionError.ReferenceGridNotFound(gridId.value)))
            }

        val session = Session.create(
            creatorId = user.userId,
            shareCode = shareCodeGenerator.generateId(),
            gridId = gridId,
            gridTemplate = grid.toGridTemplateSnapshot(),
        )
        val savedSession = sessionRepository.save(session)
        sessionGridStateCache.initialize(savedSession.id, savedSession.sessionGridState)

        logger.info("Session {} created by user {} for grid {}", savedSession.shareCode.value, user.userId(), gridId.value)
        return CocroResult.Success(savedSession.toSessionCreationSuccess())
    }
}
```

- [ ] **Step 4: Run CreateSessionUseCaseTest**

```bash
./gradlew cocro-bff:test --tests "com.cocro.application.session.usecase.CreateSessionUseCaseTest" --no-daemon 2>&1 | tail -15
```

Expected: all pass

- [ ] **Step 5: Commit**

```bash
git add cocro-bff/src/main/kotlin/com/cocro/application/session/usecase/CreateSessionUseCase.kt \
        cocro-bff/src/test/kotlin/com/cocro/application/session/usecase/CreateSessionUseCaseTest.kt
git commit -m "feat(bff): CreateSessionUseCase returns lightweight SessionCreationSuccess"
```

---

### Task 9: SynchroniseSessionUseCase — new use case

**Files:**
- Create: `cocro-bff/src/main/kotlin/com/cocro/application/session/usecase/SynchroniseSessionUseCase.kt`
- Create: `cocro-bff/src/test/kotlin/com/cocro/application/session/usecase/SynchroniseSessionUseCaseTest.kt`

- [ ] **Step 1: Write failing test**

Create `cocro-bff/src/test/kotlin/com/cocro/application/session/usecase/SynchroniseSessionUseCaseTest.kt`:
```kotlin
package com.cocro.application.session.usecase

import com.cocro.application.auth.port.CurrentUserProvider
import com.cocro.application.session.dto.SessionFullDto
import com.cocro.application.session.port.SessionGridStateCache
import com.cocro.application.session.port.SessionRepository
import com.cocro.kernel.auth.enum.Role
import com.cocro.kernel.auth.model.AuthenticatedUser
import com.cocro.kernel.auth.model.valueobject.UserId
import com.cocro.kernel.common.CocroResult
import com.cocro.kernel.grid.model.GridTemplateSnapshot
import com.cocro.kernel.grid.model.valueobject.GridShareCode
import com.cocro.kernel.session.error.SessionError
import com.cocro.kernel.session.model.Session
import com.cocro.kernel.session.model.valueobject.SessionShareCode
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class SynchroniseSessionUseCaseTest {

    private val currentUserProvider: CurrentUserProvider = mock()
    private val sessionRepository: SessionRepository = mock()
    private val sessionGridStateCache: SessionGridStateCache = mock()

    private val useCase = SynchroniseSessionUseCase(currentUserProvider, sessionRepository, sessionGridStateCache)

    private val userId = UserId.new()
    private val user = AuthenticatedUser(userId, setOf(Role.PLAYER))
    private val shareCode = SessionShareCode("AB12")
    private val gridId = GridShareCode("GRID01")
    private val template = GridTemplateSnapshot(
        shortId = gridId, title = "T", width = 5, height = 5,
        difficulty = null, author = null, reference = null,
        description = null, globalClueLabel = null,
        globalClueWordLengths = null, cells = emptyList(),
    )
    // Session with userId as participant
    private val session = Session.create(creatorId = UserId.new(), shareCode = shareCode, gridId = gridId, gridTemplate = template)
        .join(userId)

    @Test
    fun `should flush cache and return SessionFullDto`() {
        whenever(currentUserProvider.currentUserOrNull()).thenReturn(user)
        whenever(sessionRepository.findByShareCode(shareCode)).thenReturn(session)
        whenever(sessionGridStateCache.get(session.id)).thenReturn(null)

        val result = useCase.execute("AB12")

        assertThat(result).isInstanceOf(CocroResult.Success::class.java)
        val dto = (result as CocroResult.Success).value
        assertThat(dto).isInstanceOf(SessionFullDto::class.java)
        assertThat(dto.shareCode).isEqualTo("AB12")
        verify(sessionRepository).updateGridState(any(), any())
        verify(sessionGridStateCache).markFlushed(any(), any())
    }

    @Test
    fun `should return Unauthorized when user not authenticated`() {
        whenever(currentUserProvider.currentUserOrNull()).thenReturn(null)

        val result = useCase.execute("AB12")

        assertThat(result).isInstanceOf(CocroResult.Error::class.java)
        assertThat((result as CocroResult.Error).errors).contains(SessionError.Unauthorized)
    }

    @Test
    fun `should return UserNotParticipant when caller is not in session`() {
        val outsider = AuthenticatedUser(UserId.new(), setOf(Role.PLAYER))
        whenever(currentUserProvider.currentUserOrNull()).thenReturn(outsider)
        whenever(sessionRepository.findByShareCode(shareCode)).thenReturn(session)

        val result = useCase.execute("AB12")

        assertThat(result).isInstanceOf(CocroResult.Error::class.java)
        assertThat((result as CocroResult.Error).errors).anyMatch { it is SessionError.UserNotParticipant }
    }
}
```

- [ ] **Step 2: Run test to verify it FAILS (class doesn't exist)**

```bash
./gradlew cocro-bff:test --tests "com.cocro.application.session.usecase.SynchroniseSessionUseCaseTest" --no-daemon 2>&1 | tail -10
```

Expected: compile error — SynchroniseSessionUseCase not found

- [ ] **Step 3: Create SynchroniseSessionUseCase**

Create `cocro-bff/src/main/kotlin/com/cocro/application/session/usecase/SynchroniseSessionUseCase.kt`:
```kotlin
package com.cocro.application.session.usecase

import com.cocro.application.auth.port.CurrentUserProvider
import com.cocro.application.session.dto.SessionFullDto
import com.cocro.application.session.mapper.toSessionFullDto
import com.cocro.application.session.port.SessionGridStateCache
import com.cocro.application.session.port.SessionRepository
import com.cocro.kernel.common.CocroResult
import com.cocro.kernel.session.enum.InviteStatus
import com.cocro.kernel.session.error.SessionError
import com.cocro.kernel.session.model.valueobject.SessionShareCode
import com.cocro.kernel.session.rule.ParticipantsRule
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class SynchroniseSessionUseCase(
    private val currentUserProvider: CurrentUserProvider,
    private val sessionRepository: SessionRepository,
    private val sessionGridStateCache: SessionGridStateCache,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun execute(shareCode: String): CocroResult<SessionFullDto, SessionError> {
        val user = currentUserProvider.currentUserOrNull()
            ?: return CocroResult.Error(listOf(SessionError.Unauthorized))

        val sessionShareCode = runCatching { SessionShareCode(shareCode) }.getOrElse {
            return CocroResult.Error(listOf(SessionError.InvalidShareCode(shareCode)))
        }

        val session = sessionRepository.findByShareCode(sessionShareCode)
            ?: return CocroResult.Error(listOf(SessionError.SessionNotFound(shareCode)))

        if (!session.participants.any { it.userId == user.userId && it.status == InviteStatus.JOINED }) {
            logger.warn("Sync rejected: user={} is not a participant of session={}", user.userId(), shareCode)
            return CocroResult.Error(listOf(SessionError.UserNotParticipant(user.userId(), shareCode)))
        }

        val gridState = sessionGridStateCache.get(session.id) ?: session.sessionGridState
        sessionRepository.updateGridState(session.id, gridState)
        sessionGridStateCache.markFlushed(session.id, gridState.revision.value)

        val activeCount = ParticipantsRule.countActiveParticipants(session.participants)
        logger.info("Session {} synchronised by user {}", shareCode, user.userId())
        return CocroResult.Success(session.toSessionFullDto(gridState, activeCount))
    }
}
```

- [ ] **Step 4: Run SynchroniseSessionUseCaseTest**

```bash
./gradlew cocro-bff:test --tests "com.cocro.application.session.usecase.SynchroniseSessionUseCaseTest" --no-daemon 2>&1 | tail -15
```

Expected: all pass

- [ ] **Step 5: Commit**

```bash
git add cocro-bff/src/main/kotlin/com/cocro/application/session/usecase/SynchroniseSessionUseCase.kt \
        cocro-bff/src/test/kotlin/com/cocro/application/session/usecase/SynchroniseSessionUseCaseTest.kt
git commit -m "feat(bff): add SynchroniseSessionUseCase (flush + return SessionFullDto)"
```

---

### Task 10: CheckGridUseCase — end-of-game trigger

**Files:**
- Modify: `cocro-bff/src/main/kotlin/com/cocro/application/session/usecase/CheckGridUseCase.kt`
- Modify: `cocro-bff/src/test/kotlin/com/cocro/application/session/usecase/CheckGridUseCaseTest.kt` (create if missing)

- [ ] **Step 1: Write failing test for end-of-game**

Check if `CheckGridUseCaseTest.kt` exists:
```bash
ls cocro-bff/src/test/kotlin/com/cocro/application/session/usecase/CheckGridUseCaseTest.kt
```

Add this test to `CheckGridUseCaseTest`. It reuses the existing fixtures:
- `buildPlayingSession()` already exists and creates a PLAYING session with `participantId` joined
- `referenceGrid` already exists with `cells = emptyList()` — an empty grid makes `checkAgainst()` return `isComplete=true, isCorrect=true` trivially (nothing to fill, nothing wrong)
- `participantUser` maps to `participantId` — the joined participant

Add imports at the top of the test file:
```kotlin
import org.mockito.kotlin.argThat
import org.mockito.kotlin.atLeastOnce
```

```kotlin
@Test
fun `should end session and broadcast SessionEnded when grid is complete and correct`() {
    // given — empty referenceGrid → checkAgainst returns isComplete=true, isCorrect=true
    val session = buildPlayingSession()
    whenever(currentUserProvider.currentUserOrNull()).thenReturn(participantUser)
    whenever(sessionRepository.findByShareCode(shareCode)).thenReturn(session)
    whenever(sessionGridStateCache.get(session.id)).thenReturn(null)
    whenever(gridRepository.findByShortId(session.sessionGridState.gridShareCode)).thenReturn(referenceGrid)

    // when
    val result = useCase.execute("AB12")

    // then — check result is success with complete+correct
    assertThat(result).isInstanceOf(CocroResult.Success::class.java)
    val success = (result as CocroResult.Success).value
    assertThat(success.isComplete).isTrue()
    assertThat(success.isCorrect).isTrue()
    // Verify session was saved with ENDED status (end-of-game transition)
    verify(sessionRepository, atLeastOnce()).save(argThat<Session> { it.status == SessionStatus.ENDED })
    // Verify SessionEnded event broadcast
    verify(sessionNotifier).broadcast(
        session.shareCode,
        SessionEvent.SessionEnded(shareCode = "AB12", correctCount = 0, totalCount = 0),
    )
}
```

This test will FAIL until Step 2 adds the end-of-game logic (no `save(ENDED)` call exists yet).

- [ ] **Step 2: Add end-of-game logic to CheckGridUseCase**

In `cocro-bff/src/main/kotlin/com/cocro/application/session/usecase/CheckGridUseCase.kt`, after the existing flush (line ~81), add before the return:

```kotlin
// End-of-game: complete AND correct → end session
if (result.isComplete && result.isCorrect) {
    when (val endResult = session.end()) {
        is CocroResult.Success -> {
            sessionRepository.save(endResult.value)
            sessionNotifier.broadcast(
                session.shareCode,
                SessionEvent.SessionEnded(
                    shareCode = shareCode,
                    correctCount = result.correctCount,
                    totalCount = result.totalCount,
                ),
            )
            logger.info("Session {} ended: grid complete and correct ({}/{})",
                shareCode, result.correctCount, result.totalCount)
        }
        is CocroResult.Error -> {
            logger.warn("Could not end session {}: {}", shareCode, endResult.errors)
            // Continue — check result still returned to caller
        }
    }
}
```

Note: `SessionEvent.GridChecked` broadcast must happen BEFORE `SessionEnded`. Verify the order in the code.

- [ ] **Step 3: Run CheckGridUseCase tests**

```bash
./gradlew cocro-bff:test --tests "com.cocro.application.session.usecase.CheckGridUseCaseTest" --no-daemon 2>&1 | tail -15
```

- [ ] **Step 4: Commit**

```bash
git add cocro-bff/src/main/kotlin/com/cocro/application/session/usecase/CheckGridUseCase.kt \
        cocro-bff/src/test/kotlin/com/cocro/application/session/usecase/
git commit -m "feat(bff): CheckGrid triggers Session.end() on complete+correct grid"
```

---

### Task 11: LeaveSessionUseCase + HeartbeatTimeoutScheduler — detect INTERRUPTED

**Files:**
- Modify: `cocro-bff/src/main/kotlin/com/cocro/application/session/usecase/LeaveSessionUseCase.kt`
- Modify: `cocro-bff/src/main/kotlin/com/cocro/infrastructure/scheduler/HeartbeatTimeoutScheduler.kt`

- [ ] **Step 1: Add interrupt detection in LeaveSessionUseCase**

In `LeaveSessionUseCase.kt`, after `sessionRepository.save(updatedSession)` (the explicit leave persist), add:
```kotlin
// Detect: last active participant left → INTERRUPTED
val activeCount = ParticipantsRule.countActiveParticipants(updatedSession.participants)
if (activeCount == 0 && updatedSession.status == SessionStatus.PLAYING) {
    val interrupted = updatedSession.interrupt()
    sessionRepository.save(interrupted)
    sessionNotifier.broadcast(
        interrupted.shareCode,
        SessionEvent.SessionInterrupted(shareCode = interrupted.shareCode.value),
    )
    logger.info("Session {} interrupted: all participants left", sessionShareCode.value)
}
```

Add import for `SessionStatus` if needed.

- [ ] **Step 2: Add interrupt detection in HeartbeatTimeoutScheduler**

In `HeartbeatTimeoutScheduler.kt`, after the existing `ParticipantLeft` broadcast loop, add:
```kotlin
// Detect: all timed-out → INTERRUPTED
val activeCount = ParticipantsRule.countActiveParticipants(saved.participants)
if (activeCount == 0 && saved.status == SessionStatus.PLAYING) {
    val interrupted = saved.interrupt()
    sessionRepository.save(interrupted)
    sessionNotifier.broadcast(
        interrupted.shareCode,
        SessionEvent.SessionInterrupted(shareCode = interrupted.shareCode.value),
    )
    logger.info("Session {} interrupted: all participants timed out", sessionId.value)
}
```

- [ ] **Step 3: Verify compile**

```bash
./gradlew cocro-bff:compileKotlin 2>&1 | grep -E "error:"
```

- [ ] **Step 4: Commit**

```bash
git add cocro-bff/src/main/kotlin/com/cocro/application/session/usecase/LeaveSessionUseCase.kt \
        cocro-bff/src/main/kotlin/com/cocro/infrastructure/scheduler/HeartbeatTimeoutScheduler.kt
git commit -m "feat(bff): broadcast SessionInterrupted when last participant leaves/times out"
```

---

### Task 12: SessionController — add /sync endpoint + MongoDB fallback

**Files:**
- Modify: `cocro-bff/src/main/kotlin/com/cocro/presentation/rest/session/SessionController.kt`
- Modify: `cocro-bff/src/main/kotlin/com/cocro/infrastructure/persistence/mongo/session/mapper/SessionDocumentMapper.kt`

- [ ] **Step 1: Add /sync endpoint to SessionController**

In `SessionController.kt`:
- Add `SynchroniseSessionUseCase` to constructor
- Add endpoint:

```kotlin
@PostMapping("/{shareCode}/sync")
@PreAuthorize("hasAnyRole('PLAYER', 'ADMIN', 'ANONYMOUS')")
fun synchroniseSession(
    @PathVariable shareCode: String,
): ResponseEntity<*> =
    synchroniseSessionUseCase
        .execute(shareCode)
        .toResponseEntity(HttpStatus.OK)
```

- [ ] **Step 2: Add CREATING/SCORING fallback in SessionDocumentMapper**

In `SessionDocumentMapper.kt`, update `SessionDocument.toDomain()` line with `SessionStatus.valueOf(status)`:
```kotlin
// BEFORE
status = SessionStatus.valueOf(status),

// AFTER
status = when (status) {
    "CREATING", "SCORING" -> SessionStatus.PLAYING  // migration fallback
    else -> SessionStatus.valueOf(status)
},
```

- [ ] **Step 3: Run all BFF unit tests**

```bash
./gradlew cocro-bff:test --tests "com.cocro.application.*" --no-daemon 2>&1 | tail -20
```

Expected: all pass

- [ ] **Step 4: Commit**

```bash
git add cocro-bff/src/main/kotlin/com/cocro/presentation/rest/session/SessionController.kt \
        cocro-bff/src/main/kotlin/com/cocro/infrastructure/persistence/mongo/session/mapper/SessionDocumentMapper.kt
git commit -m "feat(bff): add POST /{shareCode}/sync endpoint + CREATING/SCORING MongoDB fallback"
```

---

### Task 13: BFF Integration tests update

**Files:**
- Modify: `cocro-bff/src/test/kotlin/com/cocro/integration/SessionLifecycleIT.kt`
- Modify: `cocro-bff/src/test/kotlin/com/cocro/integration/SessionWebSocketIT.kt`

- [ ] **Step 1: Update SessionLifecycleIT — create response shape**

In `SessionLifecycleIT.kt`, anywhere `createSession()` response is asserted:

- Change assertions that expected `gridTemplate`, `cells`, `participantCount` in the create response — now it only contains `sessionId` and `shareCode`.
- Update `createSession()` helper to only assert `sessionId` and `shareCode` in the body.
- Add a separate `joinSession()` helper that calls `POST /api/sessions/join` and asserts `SessionFullDto` shape.

Example updated helpers:
```kotlin
private fun createSession(gridId: String, token: String): String {
    val resp = post("/api/sessions", CreateSessionDto(gridId = gridId), token, Map::class.java)
    assertThat(resp.statusCode).isEqualTo(HttpStatus.CREATED)
    assertThat(resp.body!!["shareCode"]).isNotNull()
    // No gridTemplate, no participantCount in response
    return resp.body!!["shareCode"] as String
}

private fun joinSession(shareCode: String, token: String): Map<*, *> {
    val resp = post("/api/sessions/join", mapOf("shareCode" to shareCode), token, Map::class.java)
    assertThat(resp.statusCode).isEqualTo(HttpStatus.OK)
    assertThat(resp.body!!["status"]).isEqualTo("PLAYING")
    assertThat(resp.body!!["gridTemplate"]).isNotNull()
    return resp.body!!
}
```

Update all tests that previously called `createSession()` and expected `SessionFullDto` — they should now call `createSession()` then `joinSession()`.

- [ ] **Step 2: Add test for SessionEnded (POST /check on complete grid)**

In `SessionLifecycleIT.kt`, add a test:
```kotlin
@Test
fun `check on complete and correct grid sets session to ENDED`() {
    val gridId = createTestGrid(/* fully filled grid */)
    val shareCode = createSession(gridId, creatorToken)
    joinSession(shareCode, creatorToken)
    // Fill all correct letters via WS or setup
    // ...
    val checkResp = post("/api/sessions/$shareCode/check", null, creatorToken, Map::class.java)
    assertThat(checkResp.statusCode).isEqualTo(HttpStatus.OK)
    assertThat(checkResp.body!!["isComplete"]).isEqualTo(true)
    assertThat(checkResp.body!!["isCorrect"]).isEqualTo(true)
    // Verify session is ENDED by calling /join which should fail or return ENDED
}
```

- [ ] **Step 3: Run integration tests**

```bash
TESTCONTAINERS_RYUK_DISABLED=true ./gradlew cocro-bff:test --tests "com.cocro.integration.*" --no-daemon 2>&1 | tail -30
```

Expected: all pass

- [ ] **Step 4: Commit**

```bash
git add cocro-bff/src/test/kotlin/com/cocro/integration/
git commit -m "test(bff): update integration tests for new session lifecycle"
```

---

## Phase 4 — Angular frontend

### Task 14: Angular — models, port, HTTP adapter

**Files:**
- Modify: `cocro-angular/src/app/domain/models/session.model.ts`
- Modify: `cocro-angular/src/app/domain/models/session-events.model.ts`
- Modify: `cocro-angular/src/app/application/ports/session/game-session.port.ts`
- Modify: `cocro-angular/src/app/infrastructure/adapters/session/game-session-http.adapter.ts`

- [ ] **Step 1: Update session.model.ts**

```typescript
// cocro-angular/src/app/domain/models/session.model.ts

import { GridTemplateResponse } from './grid-template.model';

export type SessionStatus = 'PLAYING' | 'INTERRUPTED' | 'ENDED';  // removed CREATING, SCORING
export type CommandType = 'PLACE_LETTER' | 'CLEAR_CELL';

export interface CreateSessionRequest {
  gridId: string;
}

export interface JoinSessionRequest {
  shareCode: string;
}

export interface LeaveSessionRequest {
  shareCode: string;
}

// NEW: lightweight response from POST /api/sessions
export interface SessionCreatedResponse {
  sessionId: string;
  shareCode: string;
}

export interface SessionLeaveResponse {
  sessionId: string;
}

export interface CellStateDto {
  x: number;
  y: number;
  letter: string;
}

export interface SessionFullResponse {
  sessionId: string;
  shareCode: string;
  status: SessionStatus;
  participantCount: number;
  topicToSubscribe: string;
  gridTemplate: GridTemplateResponse;
  gridRevision: number;
  cells: CellStateDto[];
}

export interface SessionStateResponse {
  sessionId: string;
  shareCode: string;
  revision: number;
  cells: CellStateDto[];
}

// NEW: response from POST /api/sessions/{code}/check
export interface GridCheckResponse {
  shareCode: string;
  isComplete: boolean;
  isCorrect: boolean;
  filledCount: number;
  totalCount: number;
  wrongCount: number;
  correctCount: number;
}
```

- [ ] **Step 2: Update session-events.model.ts — add SessionEnded + SessionInterrupted**

In `cocro-angular/src/app/domain/models/session-events.model.ts`, add to the event type union and add interfaces:
```typescript
export type SessionEventType =
  | 'SessionWelcome'
  | 'ParticipantJoined'
  | 'ParticipantLeft'
  | 'GridUpdated'
  | 'GridChecked'
  | 'SyncRequired'
  | 'SessionEnded'        // NEW
  | 'SessionInterrupted'; // NEW

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

- [ ] **Step 3: Update GameSessionPort interface**

In `cocro-angular/src/app/application/ports/session/game-session.port.ts`:
```typescript
import { Observable } from 'rxjs';
import {
  CreateSessionRequest, GridCheckResponse, JoinSessionRequest, LeaveSessionRequest,
  SessionCreatedResponse, SessionFullResponse, SessionLeaveResponse, SessionStateResponse,
} from '@domain/models/session.model';

export const GAME_SESSION_PORT = new InjectionToken<GameSessionPort>('GAME_SESSION_PORT');

export interface GameSessionPort {
  createSession(dto: CreateSessionRequest): Observable<SessionCreatedResponse>;  // CHANGED
  joinSession(dto: JoinSessionRequest): Observable<SessionFullResponse>;
  leaveSession(dto: LeaveSessionRequest): Observable<SessionLeaveResponse>;
  getState(shareCode: string): Observable<SessionStateResponse>;
  syncSession(shareCode: string): Observable<SessionFullResponse>;   // NEW
  checkGrid(shareCode: string): Observable<GridCheckResponse>;       // NEW
}
```

- [ ] **Step 4: Update GameSessionHttpAdapter**

```typescript
// cocro-angular/src/app/infrastructure/adapters/session/game-session-http.adapter.ts

createSession(dto: CreateSessionRequest): Observable<SessionCreatedResponse> {
  return this.http.post<SessionCreatedResponse>(this.baseUrl, dto);
}

syncSession(shareCode: string): Observable<SessionFullResponse> {
  return this.http.post<SessionFullResponse>(`${this.baseUrl}/${shareCode}/sync`, {});
}

checkGrid(shareCode: string): Observable<GridCheckResponse> {
  return this.http.post<GridCheckResponse>(`${this.baseUrl}/${shareCode}/check`, {});
}
```

- [ ] **Step 5: Verify build**

```bash
cd cocro-angular && node --max-old-space-size=2048 node_modules/.bin/ng build 2>&1 | tail -20
```

Expected: 0 errors

- [ ] **Step 6: Commit**

```bash
git add cocro-angular/src/app/domain/models/ \
        cocro-angular/src/app/application/ports/session/ \
        cocro-angular/src/app/infrastructure/adapters/session/game-session-http.adapter.ts
git commit -m "feat(angular): update session models, port, and HTTP adapter for new lifecycle"
```

---

### Task 15: Angular — new use cases (create, leave, sync)

**Files:**
- Create: `cocro-angular/src/app/application/use-cases/create-session.use-case.ts`
- Create: `cocro-angular/src/app/application/use-cases/leave-session.use-case.ts`
- Create: `cocro-angular/src/app/application/use-cases/sync-session.use-case.ts`
- Modify: `cocro-angular/src/app/application/use-cases/join-session.use-case.ts` (ensure guest flow still works)

- [ ] **Step 1: Create CreateSessionUseCase — orchestrates create → join**

```typescript
// cocro-angular/src/app/application/use-cases/create-session.use-case.ts
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { switchMap } from 'rxjs/operators';
import { GAME_SESSION_PORT } from '@application/ports/session/game-session.port';
import { SessionFullResponse } from '@domain/models/session.model';

@Injectable({ providedIn: 'root' })
export class CreateSessionUseCase {
  private readonly sessionPort = inject(GAME_SESSION_PORT);

  execute(gridId: string): Observable<SessionFullResponse> {
    return this.sessionPort.createSession({ gridId }).pipe(
      switchMap((created) => this.sessionPort.joinSession({ shareCode: created.shareCode })),
    );
  }
}
```

- [ ] **Step 2: Create LeaveSessionUseCase**

```typescript
// cocro-angular/src/app/application/use-cases/leave-session.use-case.ts
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { GAME_SESSION_PORT } from '@application/ports/session/game-session.port';
import { SessionLeaveResponse } from '@domain/models/session.model';

@Injectable({ providedIn: 'root' })
export class LeaveSessionUseCase {
  private readonly sessionPort = inject(GAME_SESSION_PORT);

  execute(shareCode: string): Observable<SessionLeaveResponse> {
    return this.sessionPort.leaveSession({ shareCode });
  }
}
```

- [ ] **Step 3: Create SyncSessionUseCase**

```typescript
// cocro-angular/src/app/application/use-cases/sync-session.use-case.ts
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { GAME_SESSION_PORT } from '@application/ports/session/game-session.port';
import { SessionFullResponse } from '@domain/models/session.model';

@Injectable({ providedIn: 'root' })
export class SyncSessionUseCase {
  private readonly sessionPort = inject(GAME_SESSION_PORT);

  execute(shareCode: string): Observable<SessionFullResponse> {
    return this.sessionPort.syncSession(shareCode);
  }
}
```

- [ ] **Step 4: Update CreateSessionComponent to use CreateSessionUseCase**

In `cocro-angular/src/app/presentation/features/lobby/create/create-session.component.ts`:
- Replace direct `gameSession.createSession()` call with `createSessionUseCase.execute(gridId)`
- Navigate to `/play/{shareCode}` using the returned `SessionFullResponse.shareCode`

```typescript
// Inject CreateSessionUseCase instead of GAME_SESSION_PORT (or add to existing)
private readonly createSession = inject(CreateSessionUseCase);

// In submit handler:
this.createSession.execute(this.selectedGridId()).subscribe({
  next: (full) => this.router.navigate(['/play', full.shareCode]),
  error: (err) => this.error.set(getNetworkErrorMessage(err, 'Erreur à la création.')),
});
```

- [ ] **Step 5: Verify build**

```bash
cd cocro-angular && node --max-old-space-size=2048 node_modules/.bin/ng build 2>&1 | tail -20
```

Expected: 0 errors

- [ ] **Step 6: Commit**

```bash
git add cocro-angular/src/app/application/use-cases/ \
        cocro-angular/src/app/presentation/features/lobby/create/create-session.component.ts
git commit -m "feat(angular): add CreateSessionUseCase (create→join), LeaveSessionUseCase, SyncSessionUseCase"
```

---

### Task 16: Angular — GridPlayerComponent (status, sync, checkGrid, events)

**Files:**
- Modify: `cocro-angular/src/app/presentation/features/grid/play/grid-player.component.ts`
- Modify: `cocro-angular/src/app/presentation/features/grid/play/grid-player.component.html`

- [ ] **Step 1: Update status signal initial value**

```typescript
// BEFORE
readonly status = signal<SessionStatus>('CREATING');

// AFTER
readonly status = signal<SessionStatus>('PLAYING');
```

- [ ] **Step 2: Update resync() to use syncSession**

```typescript
private resync(_targetRevision: number): void {
  this.letterAuthors.set(new Map());
  this.gameSession.syncSession(this.shareCode()).subscribe({
    next: (full: SessionFullResponse) => {
      this.revision.set(full.gridRevision);
      this.participantCount.set(full.participantCount);
      this.status.set(full.status);
      full.cells.forEach((c: CellStateDto) => {
        if (c.letter) this.selector.setLetterAt(c.x, c.y, c.letter);
      });
    },
  });
}
```

- [ ] **Step 3: Add checkGrid() method + signal**

```typescript
readonly checkResult = signal<GridCheckedEvent | null>(null);

checkGrid(): void {
  this.gameSession.checkGrid(this.shareCode()).subscribe({
    next: (result: GridCheckResponse) => {
      this.checkResult.set({
        type: 'GridChecked',
        userId: this.myUserId(),
        isComplete: result.isComplete,
        correctCount: result.correctCount,
        totalCount: result.totalCount,
      } as GridCheckedEvent);
    },
  });
}
```

- [ ] **Step 4: Update handleEvent() — add SessionEnded + SessionInterrupted cases**

```typescript
private handleEvent(event: SessionEvent): void {
  switch (event.type) {
    case 'SessionWelcome':
      this.onWelcome(event as SessionWelcomeEvent);
      break;
    case 'GridUpdated':
      this.onGridUpdated(event as GridUpdatedEvent);
      break;
    case 'ParticipantJoined':
      this.participantCount.set((event as ParticipantJoinedEvent).participantCount);
      break;
    case 'ParticipantLeft':
      this.participantCount.set((event as ParticipantLeftEvent).participantCount);
      break;
    case 'GridChecked':
      this.checkResult.set(event as GridCheckedEvent);
      break;
    case 'SessionEnded':
      this.status.set('ENDED');
      break;
    case 'SessionInterrupted':
      this.status.set('INTERRUPTED');
      break;
    case 'SyncRequired':
      this.resync((event as SyncRequiredEvent).currentRevision);
      break;
  }
}
```

- [ ] **Step 5: Add imports for new types**

At the top of the component, add:
```typescript
import {
  GridCheckedEvent, GridUpdatedEvent, ParticipantJoinedEvent, ParticipantLeftEvent,
  SessionEndedEvent, SessionInterruptedEvent, SessionEvent, SessionWelcomeEvent, SyncRequiredEvent,
} from '@domain/models/session-events.model';
import { CellStateDto, GridCheckResponse, SessionFullResponse, SessionStateResponse, SessionStatus } from '@domain/models/session.model';
```

- [ ] **Step 6: Update grid-player.component.html — add Vérifier button + status feedback**

Add to the template (after existing leave button or in the action bar):
```html
<!-- Check grid button — only shown during PLAYING -->
@if (status() === 'PLAYING') {
  <button (click)="checkGrid()" class="btn-check">Vérifier</button>
}

<!-- Check result overlay -->
@if (checkResult()) {
  <div class="check-result">
    {{ checkResult()!.correctCount }} / {{ checkResult()!.totalCount }} correct
    @if (checkResult()!.isComplete) {
      <span class="complete">Grille complète !</span>
    }
  </div>
}

<!-- ENDED state -->
@if (status() === 'ENDED') {
  <div class="session-ended">
    <h2>Félicitations !</h2>
    <p>La grille est complète et correcte.</p>
  </div>
}

<!-- INTERRUPTED state -->
@if (status() === 'INTERRUPTED') {
  <div class="session-interrupted">
    <p>Session interrompue — tous les participants ont quitté.</p>
  </div>
}
```

- [ ] **Step 7: Verify build**

```bash
cd cocro-angular && node --max-old-space-size=2048 node_modules/.bin/ng build 2>&1 | tail -20
```

Expected: 0 errors, 0 warnings

- [ ] **Step 8: Commit**

```bash
git add cocro-angular/src/app/presentation/features/grid/play/
git commit -m "feat(angular): GridPlayerComponent — sync, checkGrid, SessionEnded/Interrupted handlers"
```

---

## Phase 5 — Final verification

### Task 17: Full test suite + doc update

- [ ] **Step 1: Run all BFF tests**

```bash
TESTCONTAINERS_RYUK_DISABLED=true ./gradlew cocro-bff:test --no-daemon 2>&1 | tail -30
```

Expected: BUILD SUCCESSFUL, all tests pass

- [ ] **Step 2: Run Angular build**

```bash
cd cocro-angular && node --max-old-space-size=2048 node_modules/.bin/ng build 2>&1 | tail -20
```

Expected: 0 errors

- [ ] **Step 3: Update lifecycle doc**

Update `docs/sessions/lifecycle.md` to reflect:
- 3-state machine (remove CREATING/SCORING from table)
- CreateSession returns `SessionCreationSuccess { sessionId, shareCode }`
- New `/sync` endpoint
- `SessionEnded` + `SessionInterrupted` events
- Mark divergences #1, #2 as resolved (checkGrid wired, CREATING dead code removed)

- [ ] **Step 4: Final commit**

```bash
git add docs/sessions/lifecycle.md
git commit -m "docs: update session lifecycle doc post-refactoring"
```

---

## File index

| File | Action |
|------|--------|
| `settings.gradle.kts` | Remove `:cocro-shared`, `:cocro-cmp` |
| `cocro-bff/build.gradle.kts` | Remove `implementation(project(":cocro-shared"))` |
| `cocro-bff/src/main/kotlin/com/cocro/kernel/session/enum/SessionStatus.kt` | 3 states |
| `cocro-bff/src/main/kotlin/com/cocro/kernel/session/model/Session.kt` | create() empty, rehydrate() relaxed, applyJoin(), join(), end() |
| `cocro-bff/src/main/kotlin/com/cocro/kernel/session/Participant.kt` | Remove creator(), invited() |
| `cocro-bff/src/main/kotlin/com/cocro/kernel/session/rule/ParticipantsRule.kt` | isValid() without isNotEmpty |
| `cocro-bff/src/main/kotlin/com/cocro/application/session/dto/notification/SessionEvent.kt` | +SessionEnded, +SessionInterrupted |
| `cocro-bff/src/main/kotlin/com/cocro/application/session/usecase/CreateSessionUseCase.kt` | Return SessionCreationSuccess, no heartbeat |
| `cocro-bff/src/main/kotlin/com/cocro/application/session/usecase/SynchroniseSessionUseCase.kt` | **NEW** |
| `cocro-bff/src/main/kotlin/com/cocro/application/session/usecase/CheckGridUseCase.kt` | End-of-game |
| `cocro-bff/src/main/kotlin/com/cocro/application/session/usecase/LeaveSessionUseCase.kt` | INTERRUPTED detection |
| `cocro-bff/src/main/kotlin/com/cocro/infrastructure/scheduler/HeartbeatTimeoutScheduler.kt` | INTERRUPTED detection |
| `cocro-bff/src/main/kotlin/com/cocro/presentation/rest/session/SessionController.kt` | +POST /{code}/sync |
| `cocro-bff/src/main/kotlin/com/cocro/infrastructure/persistence/mongo/session/mapper/SessionDocumentMapper.kt` | CREATING/SCORING fallback |
| `cocro-angular/src/app/domain/models/session.model.ts` | 3 statuses, +SessionCreatedResponse, +GridCheckResponse |
| `cocro-angular/src/app/domain/models/session-events.model.ts` | +SessionEnded, +SessionInterrupted |
| `cocro-angular/src/app/application/ports/session/game-session.port.ts` | createSession return, +syncSession, +checkGrid |
| `cocro-angular/src/app/infrastructure/adapters/session/game-session-http.adapter.ts` | Implement new methods |
| `cocro-angular/src/app/application/use-cases/create-session.use-case.ts` | **NEW** create→join |
| `cocro-angular/src/app/application/use-cases/leave-session.use-case.ts` | **NEW** |
| `cocro-angular/src/app/application/use-cases/sync-session.use-case.ts` | **NEW** |
| `cocro-angular/src/app/presentation/features/grid/play/grid-player.component.ts` | status, resync, checkGrid, new events |
| `cocro-angular/src/app/presentation/features/grid/play/grid-player.component.html` | Vérifier button, ENDED/INTERRUPTED UI |
| `cocro-bff/src/test/kotlin/com/cocro/kernel/session/model/SessionCreateTest.kt` | empty participants |
| `cocro-bff/src/test/kotlin/com/cocro/kernel/session/model/SessionApplyTest.kt` | INTERRUPTED join, LEFT rejoin |
| `cocro-bff/src/test/kotlin/com/cocro/kernel/session/model/SessionEndTest.kt` | no actorId |
| `cocro-bff/src/test/kotlin/com/cocro/application/session/usecase/SynchroniseSessionUseCaseTest.kt` | **NEW** |
| `cocro-bff/src/test/kotlin/com/cocro/integration/SessionLifecycleIT.kt` | create response shape |
| `cocro-bff/src/test/kotlin/com/cocro/integration/SessionWebSocketIT.kt` | SessionEnded assertion |
