# Session Refactoring — Direct Join, Embedded Grid, No Lobby Stop

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Simplify the session lifecycle so that create and join each return a single `SessionFullDto` (grid template + current state), the session is immediately PLAYING on creation, all updates are WebSocket-only, and there is no separate "start" step or template endpoint.

**Architecture:**
- `Session.create()` produces `status = PLAYING` immediately; the `Start` lifecycle command is removed.
- The MongoDB `SessionDocument` embeds a `GridTemplateDocument` (grid structure, no solution letters), populated at create time by fetching the `GridDocument` and mapping it.
- `CreateSession` and `JoinSession` both return `SessionFullDto` containing session metadata + embedded grid template + current placed-letter state. Angular no longer needs separate `GetSessionGridTemplate` or `GetSessionState` HTTP calls after joining.
- `CheckGrid` additionally flushes state to Mongo and broadcasts a `GridChecked` WS event.
- A new `GridTemplateSnapshot` value object in `cocro-shared` carries the grid structure through the domain without coupling to BFF DTOs.

**Tech Stack:** Kotlin/Spring Boot 3.2, Angular 20 Standalone + Signals, MongoDB 7, Redis 7, STOMP over WebSocket (@stomp/stompjs), Spring Data MongoDB, JUnit 5 + Mockito (BFF unit tests), Testcontainers (BFF IT), Jasmine/Karma (Angular).

---

## Files Overview

### cocro-shared (new / modified)
| Action | Path | Responsibility |
|--------|------|----------------|
| **CREATE** | `kernel/grid/model/GridTemplateSnapshot.kt` | Pure domain snapshot of grid structure (no letters) embedded in Session |
| **CREATE** | `kernel/grid/model/GridTemplateCellSnapshot.kt` | Cell entry inside GridTemplateSnapshot |
| **MODIFY** | `kernel/session/model/Session.kt` | `create()` → PLAYING; add `gridTemplate: GridTemplateSnapshot`; remove `Start` branch |
| **MODIFY** | `kernel/session/model/SessionLifecycleCommand.kt` | Remove `Start` data class |
| **MODIFY** | `kernel/session/model/state/GridCheckResult.kt` | Add `correctCount: Int` field |

### cocro-bff — Application (new / modified / deleted)
| Action | Path | Responsibility |
|--------|------|----------------|
| **MODIFY** | `application/session/dto/SessionSuccess.kt` | Add `SessionFullDto` |
| **MODIFY** | `application/session/dto/notification/SessionEvent.kt` | Add `GridChecked`; remove `SessionStarted` |
| **MODIFY** | `application/session/mapper/GridTemplateMapper.kt` | Add `GridTemplateSnapshot` ↔ `GridTemplateDto` mapping; add `Grid` → `GridTemplateSnapshot` |
| **MODIFY** | `application/session/mapper/SessionMapper.kt` | Add `Session` → `SessionFullDto` mapper |
| **MODIFY** | `application/session/usecase/CreateSessionUseCase.kt` | Inject `GridRepository` + `HeartbeatTracker`; embed template; return `SessionFullDto` |
| **MODIFY** | `application/session/usecase/JoinSessionUseCase.kt` | Return `SessionFullDto` |
| **MODIFY** | `application/session/usecase/CheckGridUseCase.kt` | Flush state + broadcast `GridChecked` |
| **DELETE** | `application/session/usecase/StartSessionUseCase.kt` | No longer needed |
| **DELETE** | `application/session/usecase/GetSessionGridTemplateUseCase.kt` | Replaced by embedded template |
| **KEEP** | `application/session/usecase/GetSessionStateUseCase.kt` | Used for WS reconnect resync |
| **KEEP** | `application/session/usecase/LeaveSessionUseCase.kt` | Unchanged |
| **KEEP** | `application/session/usecase/UpdateSessionGridUseCases.kt` | Unchanged |

### cocro-bff — Infrastructure
| Action | Path | Responsibility |
|--------|------|----------------|
| **CREATE** | `infrastructure/persistence/mongo/session/document/GridTemplateDocument.kt` | Embedded Mongo sub-document |
| **MODIFY** | `infrastructure/persistence/mongo/session/document/SessionDocument.kt` | Add `gridTemplate: GridTemplateDocument` |
| **MODIFY** | `infrastructure/persistence/mongo/session/mapper/SessionDocumentMapper.kt` | Map `GridTemplateSnapshot` ↔ `GridTemplateDocument` |
| **MODIFY** | `infrastructure/scheduler/SessionFlushScheduler.kt` | Change interval to 60 s |

### cocro-bff — Presentation
| Action | Path | Responsibility |
|--------|------|----------------|
| **MODIFY** | `presentation/rest/session/SessionController.kt` | Remove `start` + `grid-template` endpoints; update return types |

### cocro-angular — Domain & Ports
| Action | Path | Responsibility |
|--------|------|----------------|
| **MODIFY** | `domain/models/session.model.ts` | Add `SessionFullResponse` |
| **MODIFY** | `domain/models/session-events.model.ts` | Add `GridCheckedEvent`; remove `SessionStartedEvent` |
| **MODIFY** | `application/ports/session/game-session.port.ts` | Remove `startSession`; update return types |
| **DELETE** | `application/ports/session/session-grid-template.port.ts` | Replaced by `SessionFullResponse` |
| **MODIFY** | `infrastructure/adapters/session/game-session-http.adapter.ts` | Match new port |
| **DELETE** | `infrastructure/adapters/session/session-grid-template-http.adapter.ts` | No longer needed |
| **MODIFY** | `infrastructure/adapters/session/session-stomp.adapter.ts` | Handle `GridChecked` event |
| **MODIFY** | `application/use-cases/join-session.use-case.ts` | Return `SessionFullResponse` |

### cocro-angular — Presentation
| Action | Path | Responsibility |
|--------|------|----------------|
| **MODIFY** | `presentation/features/lobby/create/create-session.component.ts` | Navigate to `/play/{shareCode}` after create |
| **DELETE** | `presentation/features/lobby/room/lobby-room.component.{ts,html,scss}` | No more waiting room |
| **MODIFY** | `presentation/features/lobby/lobby.routes.ts` | Remove room route |
| **MODIFY** | `presentation/features/grid/play/grid-player.component.ts` | Simplified init: one join HTTP → init grid + state → connect WS |

---

## Task 1 — Domain: GridTemplateSnapshot in cocro-shared

**Files:**
- Create: `cocro-shared/src/commonMain/kotlin/com/cocro/kernel/grid/model/GridTemplateSnapshot.kt`
- Create: `cocro-shared/src/commonMain/kotlin/com/cocro/kernel/grid/model/GridTemplateCellSnapshot.kt`
- Modify: `cocro-shared/src/commonMain/kotlin/com/cocro/kernel/session/model/state/GridCheckResult.kt`

- [ ] **Step 1: Read `GridCheckResult.kt` and `GridTemplateMapper.kt`** to understand current shapes.

- [ ] **Step 2: Write failing test for `GridTemplateSnapshot` construction**

File: `cocro-shared/src/commonTest/kotlin/com/cocro/kernel/grid/model/GridTemplateSnapshotTest.kt`
```kotlin
class GridTemplateSnapshotTest {
    @Test
    fun `GridTemplateSnapshot holds cell list without letters`() {
        val cell = GridTemplateCellSnapshot(
            x = 0, y = 0, type = "LETTER",
            separator = null, number = 1, clues = null
        )
        val snapshot = GridTemplateSnapshot(
            gridShortId = GridShareCode("ABC123"),
            title = "Test", width = 5, height = 5,
            difficulty = null, author = null, reference = null,
            description = null, globalClueLabel = null,
            globalClueWordLengths = null, cells = listOf(cell)
        )
        assertEquals(1, snapshot.cells.size)
        assertEquals("LETTER", snapshot.cells[0].type)
    }
}
```

Run: `./gradlew cocro-shared:test`
Expected: FAIL (class not found)

- [ ] **Step 3: Create `GridTemplateCellSnapshot.kt`**

```kotlin
package com.cocro.kernel.grid.model

data class GridTemplateCellSnapshot(
    val x: Int,
    val y: Int,
    val type: String,
    val separator: String?,
    val number: Int?,
    val clues: List<GridTemplateCellClueSnapshot>?,
)

data class GridTemplateCellClueSnapshot(
    val direction: String,
    val text: String,
)
```

- [ ] **Step 4: Create `GridTemplateSnapshot.kt`**

```kotlin
package com.cocro.kernel.grid.model

import com.cocro.kernel.grid.model.valueobject.GridShareCode

data class GridTemplateSnapshot(
    val gridShortId: GridShareCode,
    val title: String,
    val width: Int,
    val height: Int,
    val difficulty: String?,
    val author: String?,
    val reference: String?,
    val description: String?,
    val globalClueLabel: String?,
    val globalClueWordLengths: List<Int>?,
    val cells: List<GridTemplateCellSnapshot>,
)
```

- [ ] **Step 5: Add `correctCount` to `GridCheckResult`**

Read the current `GridCheckResult.kt`, then add:
```kotlin
val correctCount: Int,   // = totalCount - wrongCount
```
Update any existing usages and the factory / `checkAgainst` call site in `SessionGridState.kt` to compute `correctCount = totalCount - wrongCount`.

- [ ] **Step 6: Run tests**

```bash
./gradlew cocro-shared:test
```
Expected: all pass.

- [ ] **Step 7: Commit**
```bash
git add cocro-shared/
git commit -m "feat(domain): add GridTemplateSnapshot and correctCount to GridCheckResult"
```

---

## Task 2 — Domain: Simplify Session lifecycle

**Files:**
- Modify: `cocro-shared/src/commonMain/kotlin/com/cocro/kernel/session/model/Session.kt`
- Modify: `cocro-shared/src/commonMain/kotlin/com/cocro/kernel/session/model/SessionLifecycleCommand.kt`
- Modify existing tests: `cocro-shared/src/commonTest/kotlin/com/cocro/kernel/session/model/SessionTest.kt` (if exists)

- [ ] **Step 1: Read `Session.kt` and `SessionLifecycleCommand.kt` in full**

- [ ] **Step 2: Write failing tests for new Session behavior**

```kotlin
@Test
fun `Session create produces PLAYING status immediately`() {
    val session = Session.create(
        creatorId = UserId(UUID.randomUUID()),
        shareCode = SessionShareCode("ABC123"),
        gridId = GridShareCode("GRID01"),
        gridTemplate = buildMinimalSnapshot(),
    )
    assertEquals(SessionStatus.PLAYING, session.status)
}

@Test
fun `Session creator is auto-joined`() {
    val creatorId = UserId(UUID.randomUUID())
    val session = Session.create(
        creatorId = creatorId,
        shareCode = SessionShareCode("ABC123"),
        gridId = GridShareCode("GRID01"),
        gridTemplate = buildMinimalSnapshot(),
    )
    assertTrue(session.participants.any {
        it.userId == creatorId && it.status == InviteStatus.JOINED
    })
}

@Test
fun `Session apply Join succeeds on PLAYING session`() {
    val session = Session.create(
        creatorId = UserId(UUID.randomUUID()),
        shareCode = SessionShareCode("ABC123"),
        gridId = GridShareCode("GRID01"),
        gridTemplate = buildMinimalSnapshot(),
    )
    val joiner = UserId(UUID.randomUUID())
    val result = session.apply(SessionLifecycleCommand.Join(joiner))
    assertTrue(result is CocroResult.Success)
}

private fun buildMinimalSnapshot() = GridTemplateSnapshot(
    gridShortId = GridShareCode("GRID01"),
    title = "Test", width = 5, height = 5,
    difficulty = null, author = null, reference = null,
    description = null, globalClueLabel = null,
    globalClueWordLengths = null, cells = emptyList()
)
```

Run: `./gradlew cocro-shared:test`
Expected: FAIL

- [ ] **Step 3: Update `SessionLifecycleCommand.kt`**

Remove the `Start` data class. Result:
```kotlin
sealed interface SessionLifecycleCommand {
    data class Join(val actorId: UserId) : SessionLifecycleCommand
    data class Leave(val actorId: UserId) : SessionLifecycleCommand
}
```

- [ ] **Step 4: Update `Session.kt`**

a) Change factory `create()` signature to accept `gridTemplate: GridTemplateSnapshot`:
```kotlin
fun create(
    creatorId: UserId,
    shareCode: SessionShareCode,
    gridId: GridShareCode,
    gridTemplate: GridTemplateSnapshot,
): Session
```

b) Inside `create()`, set `status = SessionStatus.PLAYING` (was CREATING).

c) Add `val gridTemplate: GridTemplateSnapshot` field to the `Session` data class.

d) Update `rehydrate()` to accept and pass through `gridTemplate: GridTemplateSnapshot?` (nullable for backward compat with old documents).

e) Remove the `apply(Start)` branch from the `when` expression. If a `Start` command is somehow sent, it should be a compile error (sealed interface exhaustion).

f) Verify the `apply(Join)` branch accepts `status ∈ {PLAYING}` (remove CREATING from the allowed set, or keep it — since CREATING is no longer produced, it is dead code either way; leave it in the `when` branch for safety).

- [ ] **Step 5: Run tests**

```bash
./gradlew cocro-shared:test
```
Expected: all pass. Fix any compilation errors (e.g., callers of `Session.create()` in `cocro-bff` tests will break — that's fine for now, fix them in later tasks).

- [ ] **Step 6: Commit**
```bash
git add cocro-shared/
git commit -m "feat(domain): session creates as PLAYING, add GridTemplateSnapshot, remove Start command"
```

---

## Task 3 — BFF: Mongo embedded documents

**Files:**
- Create: `cocro-bff/src/main/kotlin/com/cocro/infrastructure/persistence/mongo/session/document/GridTemplateDocument.kt`
- Modify: `cocro-bff/src/main/kotlin/com/cocro/infrastructure/persistence/mongo/session/document/SessionDocument.kt`

- [ ] **Step 1: Create `GridTemplateDocument.kt`**

```kotlin
package com.cocro.infrastructure.persistence.mongo.session.document

data class GridTemplateDocument(
    val gridShortId: String,
    val title: String,
    val width: Int,
    val height: Int,
    val difficulty: String?,
    val author: String?,
    val reference: String?,
    val description: String?,
    val globalClueLabel: String?,
    val globalClueWordLengths: List<Int>?,
    val cells: List<GridTemplateCellDocument>,
)

data class GridTemplateCellDocument(
    val x: Int,
    val y: Int,
    val type: String,
    val separator: String?,
    val number: Int?,
    val clues: List<GridTemplateCellClueDocument>?,
)

data class GridTemplateCellClueDocument(
    val direction: String,
    val text: String,
)
```

- [ ] **Step 2: Add `gridTemplate` field to `SessionDocument.kt`**

Read the file, then add:
```kotlin
val gridTemplate: GridTemplateDocument,
```
after `gridShortId`. (Note: `gridShortId` can stay as-is for backward compat — but in new documents, `gridTemplate.gridShortId` holds the same value.)

- [ ] **Step 3: Verify BFF compiles**

```bash
./gradlew cocro-bff:compileKotlin
```
Expected: FAIL because `SessionDocumentMapper.kt` does not yet map the new field.

- [ ] **Step 4: Commit partial**
```bash
git add cocro-bff/src/main/kotlin/com/cocro/infrastructure/persistence/mongo/session/document/
git commit -m "feat(infra): add GridTemplateDocument embedded in SessionDocument"
```

---

## Task 4 — BFF: Mongo mapper for embedded template

**Files:**
- Modify: `cocro-bff/src/main/kotlin/com/cocro/infrastructure/persistence/mongo/session/mapper/SessionDocumentMapper.kt`
- Modify: `cocro-bff/src/main/kotlin/com/cocro/application/session/mapper/GridTemplateMapper.kt`

- [ ] **Step 1: Read both mapper files in full**

- [ ] **Step 2: Add mapper functions in `GridTemplateMapper.kt`**

Add these extension functions (alongside the existing `Grid.toGridTemplateDto()`):

```kotlin
// Grid domain → GridTemplateSnapshot (for use in CreateSession)
fun Grid.toGridTemplateSnapshot(): GridTemplateSnapshot =
    GridTemplateSnapshot(
        gridShortId = this.shareCode,
        title = this.title,
        width = this.width,
        height = this.height,
        difficulty = this.metadata.difficulty?.name,
        author = this.metadata.author,
        reference = this.metadata.reference,
        description = this.metadata.description,
        globalClueLabel = this.globalClue?.label,
        globalClueWordLengths = this.globalClue?.wordLengths,
        cells = this.cells.map { it.toSnapshotCell() },
    )

private fun Cell.toSnapshotCell(): GridTemplateCellSnapshot = when (this) {
    is Cell.LetterCell -> GridTemplateCellSnapshot(
        x = pos.x, y = pos.y, type = "LETTER",
        separator = letter?.separator?.name, number = letter?.number, clues = null
    )
    is Cell.SingleClueCell -> GridTemplateCellSnapshot(
        x = pos.x, y = pos.y, type = "CLUE_SINGLE",
        separator = null, number = null,
        clues = listOf(GridTemplateCellClueSnapshot(direction = clue.direction.name, text = clue.text))
    )
    is Cell.DoubleClueCell -> GridTemplateCellSnapshot(
        x = pos.x, y = pos.y, type = "CLUE_DOUBLE",
        separator = null, number = null,
        clues = listOf(
            GridTemplateCellClueSnapshot(direction = clue1.direction.name, text = clue1.text),
            GridTemplateCellClueSnapshot(direction = clue2.direction.name, text = clue2.text),
        )
    )
    is Cell.BlackCell -> GridTemplateCellSnapshot(
        x = pos.x, y = pos.y, type = "BLACK",
        separator = null, number = null, clues = null
    )
}

// GridTemplateSnapshot → GridTemplateDocument (for Mongo persistence)
fun GridTemplateSnapshot.toDocument(): GridTemplateDocument =
    GridTemplateDocument(
        gridShortId = gridShortId.value,
        title = title, width = width, height = height,
        difficulty = difficulty, author = author, reference = reference,
        description = description, globalClueLabel = globalClueLabel,
        globalClueWordLengths = globalClueWordLengths,
        cells = cells.map { cell ->
            GridTemplateCellDocument(
                x = cell.x, y = cell.y, type = cell.type,
                separator = cell.separator, number = cell.number,
                clues = cell.clues?.map { GridTemplateCellClueDocument(it.direction, it.text) }
            )
        },
    )

// GridTemplateDocument → GridTemplateSnapshot (for rehydration from Mongo)
fun GridTemplateDocument.toSnapshot(): GridTemplateSnapshot =
    GridTemplateSnapshot(
        gridShortId = GridShareCode(gridShortId),
        title = title, width = width, height = height,
        difficulty = difficulty, author = author, reference = reference,
        description = description, globalClueLabel = globalClueLabel,
        globalClueWordLengths = globalClueWordLengths,
        cells = cells.map { cell ->
            GridTemplateCellSnapshot(
                x = cell.x, y = cell.y, type = cell.type,
                separator = cell.separator, number = cell.number,
                clues = cell.clues?.map { GridTemplateCellClueSnapshot(it.direction, it.text) }
            )
        },
    )

// GridTemplateSnapshot → GridTemplateDto (for HTTP response)
fun GridTemplateSnapshot.toDto(): GridTemplateDto =
    GridTemplateDto(
        title = title, width = width, height = height,
        difficulty = difficulty, author = author, reference = reference,
        description = description, globalClueLabel = globalClueLabel,
        globalClueWordLengths = globalClueWordLengths,
        cells = cells.map { cell ->
            GridTemplateCellDto(
                x = cell.x, y = cell.y, type = cell.type,
                separator = cell.separator, number = cell.number,
                clues = cell.clues?.map { GridTemplateClueDto(it.direction, it.text) }
            )
        },
    )
```

- [ ] **Step 3: Update `SessionDocumentMapper.kt`**

In `Session.toDocument()`, add mapping:
```kotlin
gridTemplate = session.gridTemplate?.toDocument()
    ?: error("Session ${session.id} has no gridTemplate — cannot persist"),
```
(or provide a sensible fallback if null, but after Task 2 it should never be null for new sessions)

In `Session.rehydrate()` / `SessionDocument.toDomain()`, add:
```kotlin
gridTemplate = document.gridTemplate.toSnapshot(),
```

- [ ] **Step 4: Compile check**

```bash
./gradlew cocro-bff:compileKotlin
```
Fix any remaining compile errors.

- [ ] **Step 5: Run unit tests**

```bash
./gradlew cocro-bff:test
```
Expected: existing tests may fail because `Session.create()` now requires `gridTemplate`. Fix them by providing a minimal `GridTemplateSnapshot` stub.

- [ ] **Step 6: Commit**
```bash
git add cocro-bff/src/main/kotlin/com/cocro/
git commit -m "feat(infra): map GridTemplateSnapshot ↔ GridTemplateDocument"
```

---

## Task 5 — BFF: SessionFullDto and updated SessionEvent

**Files:**
- Modify: `cocro-bff/src/main/kotlin/com/cocro/application/session/dto/SessionSuccess.kt`
- Modify: `cocro-bff/src/main/kotlin/com/cocro/application/session/dto/notification/SessionEvent.kt`
- Modify: `cocro-bff/src/main/kotlin/com/cocro/application/session/mapper/SessionMapper.kt`

- [ ] **Step 1: Add `SessionFullDto` to `SessionSuccess.kt`**

```kotlin
data class SessionFullDto(
    val sessionId: String,
    val shareCode: String,
    val status: String,
    val participantCount: Int,
    val topicToSubscribe: String,        // "/topic/session/{shareCode}"
    val gridTemplate: GridTemplateDto,
    val gridRevision: Long,
    val cells: List<CellStateDto>,
)
```

- [ ] **Step 2: Update `SessionEvent.kt`**

a) Remove `SessionStarted` from the sealed interface and from `@JsonSubTypes`.

b) Add:
```kotlin
@JsonTypeName("GridChecked")
data class GridChecked(
    val userId: String,
    val isComplete: Boolean,
    val correctCount: Int,
    val totalCount: Int,
) : SessionEvent
```

Add `GridChecked::class` to the `@JsonSubTypes` list.

- [ ] **Step 3: Add mapper function in `SessionMapper.kt`**

```kotlin
fun Session.toSessionFullDto(
    gridState: SessionGridState,
    activeParticipantCount: Int,
): SessionFullDto {
    val template = this.gridTemplate
        ?: error("Session ${this.id} has no gridTemplate")
    return SessionFullDto(
        sessionId = this.id.toString(),
        shareCode = this.shareCode.value,
        status = this.status.name,
        participantCount = activeParticipantCount,
        topicToSubscribe = "/topic/session/${this.shareCode.value}",
        gridTemplate = template.toDto(),
        gridRevision = gridState.revision.value,
        cells = gridState.cells.map { (pos, state) ->
            CellStateDto(
                x = pos.x,
                y = pos.y,
                letter = (state as SessionGridCellState.Letter).value,
            )
        },
    )
}
```

- [ ] **Step 4: Compile**

```bash
./gradlew cocro-bff:compileKotlin
```

- [ ] **Step 5: Commit**
```bash
git add cocro-bff/src/main/kotlin/com/cocro/application/session/
git commit -m "feat(app): add SessionFullDto and GridChecked event; remove SessionStarted"
```

---

## Task 6 — BFF: Refactor CreateSessionUseCase

**Files:**
- Modify: `cocro-bff/src/main/kotlin/com/cocro/application/session/usecase/CreateSessionUseCase.kt`

- [ ] **Step 1: Write failing unit test**

File: `cocro-bff/src/test/kotlin/com/cocro/application/session/usecase/CreateSessionUseCaseTest.kt`
```kotlin
@Test
fun `createSession embeds grid template and returns SessionFullDto with status PLAYING`() {
    // Given
    val user = mockUser()
    val gridId = "GRID01"
    val grid = buildTestGrid(gridId)
    val dto = CreateSessionDto(gridId = gridId)

    `when`(currentUserProvider.currentUserOrNull()).thenReturn(user)
    `when`(gridRepository.findByShortId(GridShareCode(gridId))).thenReturn(grid)
    `when`(sessionRepository.save(any())).thenAnswer { it.arguments[0] as Session }
    `when`(sessionGridStateCache.get(any())).thenReturn(null)

    // When
    val result = createSessionUseCase.execute(dto)

    // Then
    assertTrue(result is CocroResult.Success)
    val fullDto = (result as CocroResult.Success).value
    assertEquals("PLAYING", fullDto.status)
    assertNotNull(fullDto.gridTemplate)
    assertEquals(gridId, fullDto.gridTemplate.title) // adjust as needed
    assertEquals(0L, fullDto.gridRevision)
    assertTrue(fullDto.cells.isEmpty())
    verify(heartbeatTracker).markActive(any(), eq(user.userId))
    verify(heartbeatTracker).registerUserSession(eq(user.userId), any())
}

@Test
fun `createSession returns error when grid not found`() {
    val user = mockUser()
    `when`(currentUserProvider.currentUserOrNull()).thenReturn(user)
    `when`(gridRepository.findByShortId(any())).thenReturn(null)

    val result = createSessionUseCase.execute(CreateSessionDto(gridId = "NOTEXIST"))

    assertTrue(result is CocroResult.Error)
    val errors = (result as CocroResult.Error).errors
    assertTrue(errors.any { it is SessionError.ReferenceGridNotFound })
}
```

Run: `./gradlew cocro-bff:test --tests "*.CreateSessionUseCaseTest"` — Expected: FAIL

- [ ] **Step 2: Rewrite `CreateSessionUseCase.kt`**

```kotlin
@Service
class CreateSessionUseCase(
    private val currentUserProvider: CurrentUserProvider,
    private val sessionRepository: SessionRepository,
    private val sessionGridStateCache: SessionGridStateCache,
    private val gridRepository: GridRepository,
    private val heartbeatTracker: HeartbeatTracker,
    private val shareCodeGenerator: SessionCodeGenerator,
) {
    fun execute(dto: CreateSessionDto): CocroResult<SessionFullDto, SessionError> {
        val user = currentUserProvider.currentUserOrNull()
            ?: return CocroResult.Error(listOf(SessionError.Unauthorized))

        val errors = validateCreateSessionDto(dto)
        if (errors.isNotEmpty()) return CocroResult.Error(errors)

        val gridId = GridShareCode(dto.gridId)

        // Fetch and embed grid template
        val grid = gridRepository.findByShortId(gridId)
            ?: return CocroResult.Error(listOf(SessionError.ReferenceGridNotFound(dto.gridId)))

        val gridTemplate = grid.toGridTemplateSnapshot()

        // Create session (status=PLAYING, creator auto-joined)
        val session = Session.create(
            creatorId = user.userId,
            shareCode = shareCodeGenerator.generateId(),
            gridId = gridId,
            gridTemplate = gridTemplate,
        )
        val savedSession = sessionRepository.save(session)

        // Initialize cache with empty state
        sessionGridStateCache.initialize(savedSession.id, savedSession.sessionGridState)

        // Register heartbeat for creator
        heartbeatTracker.markActive(savedSession.id, user.userId)
        heartbeatTracker.registerUserSession(user.userId, savedSession.id)

        val activeCount = ParticipantsRule.countActiveParticipants(savedSession.participants)
        val gridState = sessionGridStateCache.get(savedSession.id) ?: savedSession.sessionGridState

        logger.info("Session {} created by {} for grid {}", savedSession.shareCode.value, user.userId(), gridId.value)
        return CocroResult.Success(savedSession.toSessionFullDto(gridState, activeCount))
    }
}
```

- [ ] **Step 3: Run tests**
```bash
./gradlew cocro-bff:test --tests "*.CreateSessionUseCaseTest"
```
Expected: PASS.

- [ ] **Step 4: Commit**
```bash
git add cocro-bff/src/main/kotlin/com/cocro/application/session/usecase/CreateSessionUseCase.kt
git add cocro-bff/src/test/kotlin/com/cocro/application/session/usecase/CreateSessionUseCaseTest.kt
git commit -m "feat(app): CreateSession embeds grid template, returns SessionFullDto"
```

---

## Task 7 — BFF: Refactor JoinSessionUseCase

**Files:**
- Modify: `cocro-bff/src/main/kotlin/com/cocro/application/session/usecase/JoinSessionUseCase.kt`

- [ ] **Step 1: Write failing unit test**

```kotlin
@Test
fun `joinSession returns SessionFullDto with gridTemplate and current state`() {
    val user = mockUser()
    val session = buildTestSession() // status=PLAYING, has gridTemplate, 1 participant
    val gridState = buildTestGridState(sessionId = session.id, cells = mapOf(
        CellPos(0, 0) to SessionGridCellState.Letter('A')
    ))

    `when`(currentUserProvider.currentUserOrNull()).thenReturn(user)
    `when`(sessionRepository.findByShareCode(any())).thenReturn(session)
    `when`(heartbeatTracker.isAway(any(), any())).thenReturn(false)
    `when`(sessionRepository.save(any())).thenAnswer { it.arguments[0] as Session }
    `when`(sessionGridStateCache.get(session.id)).thenReturn(gridState)

    val result = joinSessionUseCase.execute(JoinSessionDto(shareCode = "ABC123"))

    assertTrue(result is CocroResult.Success)
    val dto = (result as CocroResult.Success).value
    assertNotNull(dto.gridTemplate)
    assertEquals(1, dto.cells.size)
    assertEquals('A', dto.cells[0].letter)
    assertEquals(gridState.revision.value, dto.gridRevision)
}
```

Run: `./gradlew cocro-bff:test --tests "*.JoinSessionUseCaseTest"` — Expected: FAIL (wrong return type)

- [ ] **Step 2: Update `JoinSessionUseCase.kt`** — change return type from `SessionJoinSuccess` to `SessionFullDto` and build the DTO at the end:

In the transparent reconnect branch and at the normal success return, replace `session.toSessionJoinSuccess()` with:
```kotlin
val gridState = sessionGridStateCache.get(session.id) ?: session.sessionGridState
val activeCount = ParticipantsRule.countActiveParticipants(savedSession.participants)
CocroResult.Success(savedSession.toSessionFullDto(gridState, activeCount))
```

Ensure the mapper import `toSessionFullDto` is available.

- [ ] **Step 3: Run tests**
```bash
./gradlew cocro-bff:test --tests "*.JoinSessionUseCaseTest"
```
Expected: PASS.

- [ ] **Step 4: Commit**
```bash
git add cocro-bff/src/main/kotlin/com/cocro/application/session/usecase/JoinSessionUseCase.kt
git add cocro-bff/src/test/kotlin/com/cocro/application/session/usecase/JoinSessionUseCaseTest.kt
git commit -m "feat(app): JoinSession returns SessionFullDto"
```

---

## Task 8 — BFF: CheckGrid — flush + broadcast GridChecked

**Files:**
- Modify: `cocro-bff/src/main/kotlin/com/cocro/application/session/usecase/CheckGridUseCase.kt`

- [ ] **Step 1: Add `SessionNotifier` injection** (currently missing from `CheckGridUseCase`).

- [ ] **Step 2: Write failing test**

```kotlin
@Test
fun `checkGrid flushes state to Mongo and broadcasts GridChecked`() {
    // setup session, gridState, referenceGrid mocks...
    val result = checkGridUseCase.execute("ABC123")

    assertTrue(result is CocroResult.Success)
    verify(sessionRepository).updateGridState(eq(session.id), any())
    verify(sessionNotifier).broadcast(
        eq(session.shareCode),
        argThat { it is SessionEvent.GridChecked }
    )
}
```

- [ ] **Step 3: Update `CheckGridUseCase.kt`**

After `val result = gridState.checkAgainst(referenceGrid)`, before `return CocroResult.Success(...)`, add:

```kotlin
// Flush state to Mongo on every check
sessionRepository.updateGridState(session.id, gridState)
sessionGridStateCache.markFlushed(session.id, gridState.revision.value)

// Broadcast to all participants that a check was done
sessionNotifier.broadcast(
    sessionShareCode,
    SessionEvent.GridChecked(
        userId = user.userId(),
        isComplete = result.isComplete,
        correctCount = result.correctCount,
        totalCount = result.totalCount,
    ),
)
```

- [ ] **Step 4: Run tests and commit**
```bash
./gradlew cocro-bff:test --tests "*.CheckGridUseCaseTest"
git add cocro-bff/src/main/kotlin/com/cocro/application/session/usecase/CheckGridUseCase.kt
git add cocro-bff/src/test/kotlin/com/cocro/application/session/usecase/CheckGridUseCaseTest.kt
git commit -m "feat(app): CheckGrid flushes state and broadcasts GridChecked event"
```

---

## Task 9 — BFF: Delete StartSessionUseCase and GetSessionGridTemplateUseCase

**Files:**
- Delete: `cocro-bff/src/main/kotlin/com/cocro/application/session/usecase/StartSessionUseCase.kt`
- Delete: `cocro-bff/src/main/kotlin/com/cocro/application/session/usecase/GetSessionGridTemplateUseCase.kt`
- Delete their test files if present

- [ ] **Step 1: Delete both use case files and their tests**

```bash
rm cocro-bff/src/main/kotlin/com/cocro/application/session/usecase/StartSessionUseCase.kt
rm cocro-bff/src/main/kotlin/com/cocro/application/session/usecase/GetSessionGridTemplateUseCase.kt
```

- [ ] **Step 2: Update `SessionController.kt`**

Read the file. Remove:
- `startSession()` endpoint (`POST /api/sessions/start`)
- `getSessionGridTemplate()` endpoint (`GET /api/sessions/{shareCode}/grid-template`)
- Corresponding `@Autowired`/constructor injections for `StartSessionUseCase` and `GetSessionGridTemplateUseCase`

Update return types of `createSession` and `joinSession` to return `ResponseEntity<SessionFullDto>`.

- [ ] **Step 3: Compile + test**
```bash
./gradlew cocro-bff:build
```
Expected: 0 errors.

- [ ] **Step 4: Commit**
```bash
git add cocro-bff/
git commit -m "feat(app): remove StartSession and GetSessionGridTemplate; update controller return types"
```

---

## Task 10 — BFF: Update SessionFlushScheduler interval

**Files:**
- Modify: `cocro-bff/src/main/kotlin/com/cocro/infrastructure/scheduler/SessionFlushScheduler.kt`

- [ ] **Step 1: Read the file and find the `@Scheduled` annotation or config value**

- [ ] **Step 2: Change the flush interval to 60 seconds**

If it uses `@Value("${cocro.session.flush.interval:30000}")`, update the default to `60000`.
If it uses a hardcoded value, change `30000` → `60000`.

- [ ] **Step 3: Commit**
```bash
git add cocro-bff/src/main/kotlin/com/cocro/infrastructure/scheduler/SessionFlushScheduler.kt
git commit -m "feat(infra): increase session flush interval to 60s"
```

---

## Task 11 — BFF: Update integration test SessionWebSocketIT

**Files:**
- Modify: `cocro-bff/src/test/kotlin/com/cocro/presentation/websocket/SessionWebSocketIT.kt`

- [ ] **Step 1: Read the current IT test in full**

- [ ] **Step 2: Update the test flow**

Remove the "start session" HTTP call step. The test flow should be:

```
1. Create session (POST /api/sessions) → expect SessionFullDto (status=PLAYING)
2. Second user joins (POST /api/sessions/join) → expect SessionFullDto with gridTemplate populated
3. Connect both users to WS
4. Subscribe both to /app/session/{shareCode}/welcome
5. First user sends a grid update → both receive GridUpdated
6. User calls check → both receive GridChecked
7. User leaves → other receives ParticipantLeft
```

Key assertions to add:
- After create: `assertEquals("PLAYING", fullDto.status)`
- After join: `assertNotNull(fullDto.gridTemplate)`, `assertNotNull(fullDto.gridTemplate.cells)`
- After check: verify `GridChecked` event is broadcast (check `type == "GridChecked"` in the received map)

Remove:
- Any assertion waiting for `SessionStarted` event
- The `POST /api/sessions/start` call

- [ ] **Step 3: Run IT**
```bash
TESTCONTAINERS_RYUK_DISABLED=true ./gradlew cocro-bff:test --tests "*.SessionWebSocketIT" --no-daemon
```
Expected: PASS.

- [ ] **Step 4: Commit**
```bash
git add cocro-bff/src/test/kotlin/com/cocro/presentation/websocket/SessionWebSocketIT.kt
git commit -m "test(it): update SessionWebSocketIT for direct-join flow (no start step)"
```

---

## Task 12 — Angular: Update domain models

**Files:**
- Modify: `cocro-angular/src/app/domain/models/session.model.ts`
- Modify: `cocro-angular/src/app/domain/models/session-events.model.ts`

- [ ] **Step 1: Add `SessionFullResponse` to `session.model.ts`**

```typescript
export interface SessionFullResponse {
  sessionId: string;
  shareCode: string;
  status: SessionStatus;
  participantCount: number;
  topicToSubscribe: string;
  gridTemplate: GridTemplateResponse;  // import from grid-template.model.ts
  gridRevision: number;
  cells: CellStateDto[];
}
```

Keep `GridTemplateResponse` and `CellStateDto` where they are. Remove `SessionCreationResponse` and `SessionJoinResponse` (they are replaced by `SessionFullResponse`). Keep `SessionStateResponse` for `GetSessionState` resync endpoint if still used.

- [ ] **Step 2: Update `session-events.model.ts`**

Remove `SessionStartedEvent` from the discriminated union.

Add:
```typescript
export interface GridCheckedEvent {
  type: 'GridChecked';
  userId: string;
  isComplete: boolean;
  correctCount: number;
  totalCount: number;
}
```

Add `GridCheckedEvent` to the `SessionEvent` union type.

- [ ] **Step 3: Build check**
```bash
cd cocro-angular && npx ng build
```
Expected: compile errors pointing to usages of removed types — fix them progressively in the next tasks.

- [ ] **Step 4: Commit what compiles**
```bash
git add cocro-angular/src/app/domain/models/
git commit -m "feat(ng-domain): add SessionFullResponse, GridCheckedEvent; remove SessionStartedEvent"
```

---

## Task 13 — Angular: Remove grid-template port & adapter; update game-session port

**Files:**
- Delete: `cocro-angular/src/app/application/ports/session/session-grid-template.port.ts`
- Delete: `cocro-angular/src/app/infrastructure/adapters/session/session-grid-template-http.adapter.ts`
- Modify: `cocro-angular/src/app/application/ports/session/game-session.port.ts`
- Modify: `cocro-angular/src/app/infrastructure/adapters/session/game-session-http.adapter.ts`

- [ ] **Step 1: Delete the grid-template port and adapter files**

```bash
rm cocro-angular/src/app/application/ports/session/session-grid-template.port.ts
rm cocro-angular/src/app/infrastructure/adapters/session/session-grid-template-http.adapter.ts
```

Remove DI registration of the adapter (look in the module/app.config.ts for `SessionGridTemplatePort` provider — remove it).

- [ ] **Step 2: Update `game-session.port.ts`**

```typescript
export interface GameSessionPort {
  createSession(dto: CreateSessionRequest): Observable<SessionFullResponse>;
  joinSession(dto: JoinSessionRequest): Observable<SessionFullResponse>;
  leaveSession(dto: LeaveSessionRequest): Observable<LeaveSessionResponse>;
  getState(shareCode: string): Observable<SessionStateResponse>; // keep for resync
}
```

Remove `startSession` signature entirely.

- [ ] **Step 3: Update `game-session-http.adapter.ts`**

Match the new port: `createSession` and `joinSession` return `SessionFullResponse`. Remove `startSession` method. Keep `getState`.

```typescript
createSession(dto: CreateSessionRequest): Observable<SessionFullResponse> {
  return this.http.post<SessionFullResponse>('/api/sessions', dto);
}

joinSession(dto: JoinSessionRequest): Observable<SessionFullResponse> {
  return this.http.post<SessionFullResponse>('/api/sessions/join', dto);
}
```

- [ ] **Step 4: Update `session-stomp.adapter.ts`**

Add handling for `GridChecked` event type in the event routing. Typically in the `onMessage` callback:
```typescript
case 'GridChecked':
  onEvent(event as GridCheckedEvent);
  break;
```

Remove any handling for `SessionStarted` (or leave a `default` fallback).

- [ ] **Step 5: Build**
```bash
cd cocro-angular && npx ng build
```

- [ ] **Step 6: Commit**
```bash
git add cocro-angular/src/app/
git commit -m "feat(ng-infra): remove grid-template port/adapter; update GameSession port for SessionFullResponse"
```

---

## Task 14 — Angular: Update join use-case and create-session component

**Files:**
- Modify: `cocro-angular/src/app/application/use-cases/join-session.use-case.ts`
- Modify: `cocro-angular/src/app/presentation/features/lobby/create/create-session.component.ts`

- [ ] **Step 1: Update `join-session.use-case.ts`**

Return `SessionFullResponse` instead of `SessionJoinResponse`:
```typescript
execute(shareCode: string): Observable<SessionFullResponse> {
  // existing guest handling stays the same
  // change the HTTP call return type expectation
  return this.gameSessionPort.joinSession({ shareCode });
}
```

Update the caller in `front-panel.component.ts` (or wherever `JoinSessionUseCase` is called) to navigate to `/play/{shareCode}` using the response's `shareCode`:
```typescript
this.joinSessionUseCase.execute(shareCode).subscribe({
  next: (fullDto) => this.router.navigate(['/play', fullDto.shareCode]),
  error: ...
});
```

- [ ] **Step 2: Update `create-session.component.ts`**

After calling `createSession`, navigate directly to `/play/{shareCode}`:
```typescript
this.gameSessionPort.createSession({ gridId: this.gridId }).subscribe({
  next: (fullDto) => this.router.navigate(['/play', fullDto.shareCode]),
  error: ...
});
```

Remove navigation to `/lobby/room/{shareCode}`.

- [ ] **Step 3: Build**
```bash
cd cocro-angular && npx ng build
```

- [ ] **Step 4: Commit**
```bash
git add cocro-angular/src/app/application/use-cases/join-session.use-case.ts
git add cocro-angular/src/app/presentation/features/lobby/create/create-session.component.ts
git commit -m "feat(ng-app): create and join navigate directly to /play; return SessionFullResponse"
```

---

## Task 15 — Angular: Simplify grid-player component

**Files:**
- Modify: `cocro-angular/src/app/presentation/features/grid/play/grid-player.component.ts`
- Modify: `cocro-angular/src/app/presentation/features/grid/play/grid-player.component.html` (if needed)

- [ ] **Step 1: Read the current `grid-player.component.ts` in full**

- [ ] **Step 2: Rewrite `ngOnInit` flow**

Replace the current multi-step init (connect WS → get welcome → fetch template → fetch state) with:

```typescript
ngOnInit() {
  const shareCode = this.route.snapshot.params['shareCode'];

  // 1. HTTP join → get full session data
  this.gameSessionPort.joinSession({ shareCode }).subscribe({
    next: (fullDto: SessionFullResponse) => {
      // 2. Init grid structure from template
      const grid = mapTemplateToGrid(fullDto.gridTemplate); // existing mapping function
      this.gridSelector.initGrid(grid);

      // 3. Apply placed letters from current state
      fullDto.cells.forEach(cell =>
        this.gridSelector.setLetterAt(cell.x, cell.y, cell.letter)
      );

      this.revision.set(fullDto.gridRevision);
      this.participantCount.set(fullDto.participantCount);
      this.shareCode.set(fullDto.shareCode);
      this.status.set(fullDto.status);
      this.gridLoaded.set(true);

      // 4. Connect WebSocket for real-time updates
      const token = this.authService.getToken() ?? '';
      this.sessionSocket.connect(token, shareCode, (event) => this.handleEvent(event));
    },
    error: (err) => {
      this.error.set(getNetworkErrorMessage(err, 'Impossible de rejoindre la session.'));
    }
  });
}
```

Remove the old multi-step WS welcome → HTTP template → HTTP state sequence.

Keep `handleEvent()` method as-is but add handling for `GridChecked`:
```typescript
case 'GridChecked': {
  const e = event as GridCheckedEvent;
  // Show notification: "User X checked the grid — N/M correct"
  // e.g., update a signal: this.lastCheckResult.set(e)
  break;
}
```

Remove the `SessionStarted` case from the event handler.

- [ ] **Step 3: Remove unused injections**

Remove `SessionGridTemplatePort` injection if present. Remove the now-unused `getSessionTemplate$` observable chain.

- [ ] **Step 4: Build and check for 0 errors**
```bash
cd cocro-angular && npx ng build
```
Expected: 0 errors, 0 warnings.

- [ ] **Step 5: Commit**
```bash
git add cocro-angular/src/app/presentation/features/grid/play/
git commit -m "feat(ng-play): simplified grid-player init — one join HTTP call, then WS"
```

---

## Task 16 — Angular: Remove lobby-room; update routing

**Files:**
- Delete: `cocro-angular/src/app/presentation/features/lobby/room/lobby-room.component.ts`
- Delete: `cocro-angular/src/app/presentation/features/lobby/room/lobby-room.component.html`
- Delete: `cocro-angular/src/app/presentation/features/lobby/room/lobby-room.component.scss`
- Modify: `cocro-angular/src/app/presentation/features/lobby/lobby.routes.ts`

- [ ] **Step 1: Delete lobby-room component files**
```bash
rm cocro-angular/src/app/presentation/features/lobby/room/lobby-room.component.*
```

- [ ] **Step 2: Update `lobby.routes.ts`**

Remove the `room/:shareCode` route. If a user somehow lands on `/lobby/room/:shareCode`, redirect to `/play/:shareCode`:
```typescript
{
  path: 'room/:shareCode',
  redirectTo: '/play/:shareCode',
  pathMatch: 'full'
}
```
Or just remove it entirely.

- [ ] **Step 3: Build**
```bash
cd cocro-angular && npx ng build
```
Expected: 0 errors.

- [ ] **Step 4: Commit**
```bash
git add cocro-angular/src/app/presentation/features/lobby/
git commit -m "feat(ng-lobby): remove waiting-room; create navigates directly to /play"
```

---

## Task 17 — Angular: Final build and cleanup

- [ ] **Step 1: Full build**
```bash
cd cocro-angular && npx ng build
```
Expected: 0 errors, 0 warnings.

- [ ] **Step 2: Run unit tests**
```bash
cd cocro-angular && npx ng test --watch=false
```
Fix any failing tests. Key tests to update:
- `create-session.component.spec.ts`: expect navigation to `/play/{shareCode}`, not `/lobby/room`
- `grid-player.component.spec.ts`: mock one `joinSession` HTTP call that returns a `SessionFullResponse`; remove mocks for `getGridTemplate` and `getState`

- [ ] **Step 3: Final BFF build**
```bash
./gradlew build
```
Expected: BUILD SUCCESSFUL, 0 test failures.

- [ ] **Step 4: Final commit**
```bash
git add .
git commit -m "chore: final cleanup after session refactoring — direct join, embedded grid"
```

---

## Test Coverage Summary

### Tests to delete
- `StartSessionUseCaseTest.kt`
- `GetSessionGridTemplateUseCaseTest.kt`
- `lobby-room.component.spec.ts`

### Tests to update
- `SessionWebSocketIT.kt` — remove start step, verify SessionFullDto shape on create/join, verify GridChecked broadcast
- `JoinSessionUseCaseTest.kt` — update expected return type to `SessionFullDto`
- `CreateSessionUseCaseTest.kt` — verify grid embedding, heartbeat, PLAYING status
- `CheckGridUseCaseTest.kt` — verify flush + GridChecked broadcast
- `SessionTest.kt` (cocro-shared) — verify create() → PLAYING, Join on PLAYING works
- `create-session.component.spec.ts` — navigate to `/play/` not `/lobby/room/`
- `grid-player.component.spec.ts` — one HTTP join call, no separate template/state calls

### New tests to write
- `GridTemplateSnapshotTest.kt` (cocro-shared)
- `GridTemplateMapperTest.kt` (BFF) — round-trip: Grid → GridTemplateSnapshot → GridTemplateDocument → GridTemplateSnapshot → GridTemplateDto

---

## Key Invariants to Preserve

1. **STOMP auth** (`StompAuthChannelInterceptor` + `ExecutorChannelInterceptor`) — do not change.
2. **CAS update in Redis** (Lua script in `RedisSessionGridStateCache`) — do not change.
3. **Threshold flush** (every 50 revisions in `UpdateSessionGridUseCases`) — do not change.
4. **Heartbeat timeout** (`HeartbeatTimeoutScheduler`) — do not change.
5. **`GetSessionStateUseCase`** — keep as-is, used by clients for WS reconnect resync via `SyncRequired` event.
6. **`LeaveSessionUseCase`** — keep as-is.
7. **`UpdateSessionGridUseCases`** — keep as-is (WS grid updates unchanged).
