# BFF Test Coverage & Documentation Alignment

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Complete unit test coverage for all BFF use cases, enrich integration tests with full game scenarios, and align documentation with actual implementation.

**Architecture:** Kotlin/Spring Boot clean architecture. Unit tests mock ports (repositories, caches, notifiers). Integration tests use Testcontainers (MongoDB 7 + Redis 7) and exercise the full HTTP/WebSocket stack.

**Tech Stack:** Kotlin, JUnit 5, Mockito-Kotlin, AssertJ, Testcontainers, Spring Boot Test, WebSocketStompClient

---

## Chunk 1: Unit Tests for Missing Use Cases

### Task 1: GetSessionStateUseCaseTest

**Files:**
- Create: `cocro-bff/src/test/kotlin/com/cocro/application/session/usecase/GetSessionStateUseCaseTest.kt`

- [ ] **Step 1: Write test file with all test cases**

```kotlin
package com.cocro.application.session.usecase

import com.cocro.application.auth.port.CurrentUserProvider
import com.cocro.application.session.port.SessionGridStateCache
import com.cocro.application.session.port.SessionRepository
import com.cocro.kernel.auth.enum.Role
import com.cocro.kernel.auth.model.AuthenticatedUser
import com.cocro.kernel.auth.model.valueobject.UserId
import com.cocro.kernel.common.CocroResult
import com.cocro.kernel.grid.model.CellPos
import com.cocro.kernel.grid.model.valueobject.GridShareCode
import com.cocro.kernel.session.enum.InviteStatus
import com.cocro.kernel.session.enum.SessionStatus
import com.cocro.kernel.session.error.SessionError
import com.cocro.kernel.session.model.Session
import com.cocro.kernel.session.model.state.SessionGridCellState
import com.cocro.kernel.session.model.state.SessionGridState
import com.cocro.kernel.session.model.valueobject.SessionShareCode
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class GetSessionStateUseCaseTest {

    private val currentUserProvider: CurrentUserProvider = mock()
    private val sessionRepository: SessionRepository = mock()
    private val sessionGridStateCache: SessionGridStateCache = mock()

    private val useCase = GetSessionStateUseCase(currentUserProvider, sessionRepository, sessionGridStateCache)

    private val creatorId = UserId.new()
    private val participantId = UserId.new()
    private val participantUser = AuthenticatedUser(participantId, setOf(Role.PLAYER))
    private val shareCode = SessionShareCode("AB12")
    private val gridId = GridShareCode("GRID01")

    private fun buildPlayingSessionWithParticipant(): Session {
        val base = Session.create(creatorId = creatorId, shareCode = shareCode, gridId = gridId)
        val withParticipant = base.join(participantId)
        return Session.rehydrate(
            id = base.id,
            shareCode = base.shareCode,
            creatorId = base.creatorId,
            gridId = base.gridId,
            status = SessionStatus.PLAYING,
            participants = withParticipant.participants,
            sessionGridState = base.sessionGridState,
            createdAt = base.createdAt,
            updatedAt = base.updatedAt,
        )
    }

    @Test
    fun `should return session state from cache when available`() {
        val session = buildPlayingSessionWithParticipant()
        val cachedState = session.sessionGridState.copy(
            cells = mapOf(CellPos(0, 0) to SessionGridCellState.Letter('A')),
        )
        whenever(currentUserProvider.currentUserOrNull()).thenReturn(participantUser)
        whenever(sessionRepository.findByShareCode(shareCode)).thenReturn(session)
        whenever(sessionGridStateCache.get(session.id)).thenReturn(cachedState)

        val result = useCase.execute("AB12")

        assertThat(result).isInstanceOf(CocroResult.Success::class.java)
        val dto = (result as CocroResult.Success).value
        assertThat(dto.cells).hasSize(1)
        assertThat(dto.cells[0].x).isEqualTo(0)
        assertThat(dto.cells[0].y).isEqualTo(0)
        assertThat(dto.cells[0].letter).isEqualTo('A')
    }

    @Test
    fun `should fall back to embedded state when cache miss`() {
        val session = buildPlayingSessionWithParticipant()
        whenever(currentUserProvider.currentUserOrNull()).thenReturn(participantUser)
        whenever(sessionRepository.findByShareCode(shareCode)).thenReturn(session)
        whenever(sessionGridStateCache.get(session.id)).thenReturn(null)

        val result = useCase.execute("AB12")

        assertThat(result).isInstanceOf(CocroResult.Success::class.java)
        val dto = (result as CocroResult.Success).value
        assertThat(dto.revision).isEqualTo(0L)
        assertThat(dto.cells).isEmpty()
    }

    @Test
    fun `should return Unauthorized when user is not authenticated`() {
        whenever(currentUserProvider.currentUserOrNull()).thenReturn(null)

        val result = useCase.execute("AB12")

        assertThat(result).isInstanceOf(CocroResult.Error::class.java)
        assertThat((result as CocroResult.Error).errors).contains(SessionError.Unauthorized)
        verifyNoInteractions(sessionRepository)
    }

    @Test
    fun `should return InvalidShareCode when shareCode format is invalid`() {
        whenever(currentUserProvider.currentUserOrNull()).thenReturn(participantUser)

        val result = useCase.execute("bad!")

        assertThat(result).isInstanceOf(CocroResult.Error::class.java)
        assertThat((result as CocroResult.Error).errors).anyMatch { it is SessionError.InvalidShareCode }
        verifyNoInteractions(sessionRepository)
    }

    @Test
    fun `should return SessionNotFound when session does not exist`() {
        whenever(currentUserProvider.currentUserOrNull()).thenReturn(participantUser)
        whenever(sessionRepository.findByShareCode(shareCode)).thenReturn(null)

        val result = useCase.execute("AB12")

        assertThat(result).isInstanceOf(CocroResult.Error::class.java)
        assertThat((result as CocroResult.Error).errors).anyMatch { it is SessionError.SessionNotFound }
    }

    @Test
    fun `should return UserNotParticipant when user is not a joined participant`() {
        val outsider = AuthenticatedUser(UserId.new(), setOf(Role.PLAYER))
        val session = buildPlayingSessionWithParticipant()
        whenever(currentUserProvider.currentUserOrNull()).thenReturn(outsider)
        whenever(sessionRepository.findByShareCode(shareCode)).thenReturn(session)

        val result = useCase.execute("AB12")

        assertThat(result).isInstanceOf(CocroResult.Error::class.java)
        assertThat((result as CocroResult.Error).errors).anyMatch { it is SessionError.UserNotParticipant }
    }
}
```

- [ ] **Step 2: Run test to verify it compiles and passes**

Run: `TESTCONTAINERS_RYUK_DISABLED=true ./gradlew cocro-bff:test --tests "com.cocro.application.session.usecase.GetSessionStateUseCaseTest" --no-daemon`
Expected: 6 tests PASS

- [ ] **Step 3: Commit**

```bash
git add cocro-bff/src/test/kotlin/com/cocro/application/session/usecase/GetSessionStateUseCaseTest.kt
git commit -m "[BFF] Add GetSessionStateUseCase unit tests (cache hit, cache miss, auth, validation)"
```

---

### Task 2: UpdateSessionGridUseCasesTest

**Files:**
- Create: `cocro-bff/src/test/kotlin/com/cocro/application/session/usecase/UpdateSessionGridUseCasesTest.kt`

- [ ] **Step 1: Write test file with all test cases**

```kotlin
package com.cocro.application.session.usecase

import com.cocro.application.auth.port.CurrentUserProvider
import com.cocro.application.session.dto.UpdateSessionGridDto
import com.cocro.application.session.port.SessionGridStateCache
import com.cocro.application.session.port.SessionNotifier
import com.cocro.application.session.port.SessionRepository
import com.cocro.kernel.auth.enum.Role
import com.cocro.kernel.auth.model.AuthenticatedUser
import com.cocro.kernel.auth.model.valueobject.UserId
import com.cocro.kernel.common.CocroResult
import com.cocro.kernel.grid.model.valueobject.GridShareCode
import com.cocro.kernel.session.enum.SessionStatus
import com.cocro.kernel.session.error.SessionError
import com.cocro.kernel.session.model.Session
import com.cocro.kernel.session.model.state.SessionGridState
import com.cocro.kernel.session.model.valueobject.SessionShareCode
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class UpdateSessionGridUseCasesTest {

    private val currentUserProvider: CurrentUserProvider = mock()
    private val sessionRepository: SessionRepository = mock()
    private val sessionGridStateCache: SessionGridStateCache = mock()
    private val sessionNotifier: SessionNotifier = mock()

    private val useCase = UpdateSessionGridUseCases(
        currentUserProvider, sessionRepository, sessionGridStateCache, sessionNotifier, 50L,
    )

    private val creatorId = UserId.new()
    private val shareCode = SessionShareCode("AB12")
    private val gridId = GridShareCode("GRID01")
    private val authenticatedUser = AuthenticatedUser(creatorId, setOf(Role.PLAYER))

    private fun buildPlayingSession(): Session {
        val base = Session.create(creatorId = creatorId, shareCode = shareCode, gridId = gridId)
        return Session.rehydrate(
            id = base.id, shareCode = base.shareCode, creatorId = base.creatorId,
            gridId = base.gridId, status = SessionStatus.PLAYING,
            participants = base.participants, sessionGridState = base.sessionGridState,
            createdAt = base.createdAt, updatedAt = base.updatedAt,
        )
    }

    private fun placeLetterDto(sc: String = "AB12") = UpdateSessionGridDto(
        shareCode = sc, posX = 1, posY = 2, commandType = "PLACE_LETTER", letter = 'A',
    )

    @Test
    fun `should place letter successfully and broadcast`() {
        val session = buildPlayingSession()
        whenever(currentUserProvider.currentUserOrNull()).thenReturn(authenticatedUser)
        whenever(sessionRepository.findByShareCode(shareCode)).thenReturn(session)
        whenever(sessionGridStateCache.get(session.id)).thenReturn(session.sessionGridState)
        whenever(sessionGridStateCache.compareAndSet(eq(session.id), eq(0L), any())).thenReturn(1L)
        whenever(sessionGridStateCache.getLastFlushedRevision(session.id)).thenReturn(0L)

        val result = useCase.execute(placeLetterDto())

        assertThat(result).isInstanceOf(CocroResult.Success::class.java)
        val success = (result as CocroResult.Success).value
        assertThat(success.commandType).isEqualTo("PLACE_LETTER")
        verify(sessionNotifier).broadcast(eq(shareCode), any())
    }

    @Test
    fun `should flush to MongoDB when threshold is reached`() {
        val session = buildPlayingSession()
        whenever(currentUserProvider.currentUserOrNull()).thenReturn(authenticatedUser)
        whenever(sessionRepository.findByShareCode(shareCode)).thenReturn(session)
        whenever(sessionGridStateCache.get(session.id)).thenReturn(session.sessionGridState)
        whenever(sessionGridStateCache.compareAndSet(eq(session.id), eq(0L), any())).thenReturn(1L)
        whenever(sessionGridStateCache.getLastFlushedRevision(session.id)).thenReturn(0L)

        // Use a threshold of 1 to trigger flush immediately
        val lowThresholdUseCase = UpdateSessionGridUseCases(
            currentUserProvider, sessionRepository, sessionGridStateCache, sessionNotifier, 1L,
        )
        lowThresholdUseCase.execute(placeLetterDto())

        verify(sessionRepository).updateGridState(eq(session.id), any())
        verify(sessionGridStateCache).markFlushed(eq(session.id), any())
    }

    @Test
    fun `should not flush when below threshold`() {
        val session = buildPlayingSession()
        whenever(currentUserProvider.currentUserOrNull()).thenReturn(authenticatedUser)
        whenever(sessionRepository.findByShareCode(shareCode)).thenReturn(session)
        whenever(sessionGridStateCache.get(session.id)).thenReturn(session.sessionGridState)
        whenever(sessionGridStateCache.compareAndSet(eq(session.id), eq(0L), any())).thenReturn(1L)
        whenever(sessionGridStateCache.getLastFlushedRevision(session.id)).thenReturn(0L)

        useCase.execute(placeLetterDto())

        verify(sessionRepository, never()).updateGridState(any(), any())
    }

    @Test
    fun `should return Unauthorized when user is not authenticated`() {
        whenever(currentUserProvider.currentUserOrNull()).thenReturn(null)

        val result = useCase.execute(placeLetterDto())

        assertThat(result).isInstanceOf(CocroResult.Error::class.java)
        assertThat((result as CocroResult.Error).errors).contains(SessionError.Unauthorized)
        verifyNoInteractions(sessionRepository)
    }

    @Test
    fun `should return validation error for invalid shareCode`() {
        whenever(currentUserProvider.currentUserOrNull()).thenReturn(authenticatedUser)

        val result = useCase.execute(placeLetterDto(sc = "bad!"))

        assertThat(result).isInstanceOf(CocroResult.Error::class.java)
        verifyNoInteractions(sessionRepository)
    }

    @Test
    fun `should return SessionNotFound when session does not exist`() {
        whenever(currentUserProvider.currentUserOrNull()).thenReturn(authenticatedUser)
        whenever(sessionRepository.findByShareCode(shareCode)).thenReturn(null)

        val result = useCase.execute(placeLetterDto())

        assertThat(result).isInstanceOf(CocroResult.Error::class.java)
        assertThat((result as CocroResult.Error).errors).anyMatch { it is SessionError.SessionNotFound }
    }

    @Test
    fun `should use ERASE_LETTER command type`() {
        val session = buildPlayingSession()
        whenever(currentUserProvider.currentUserOrNull()).thenReturn(authenticatedUser)
        whenever(sessionRepository.findByShareCode(shareCode)).thenReturn(session)
        whenever(sessionGridStateCache.get(session.id)).thenReturn(session.sessionGridState)
        whenever(sessionGridStateCache.compareAndSet(eq(session.id), eq(0L), any())).thenReturn(1L)
        whenever(sessionGridStateCache.getLastFlushedRevision(session.id)).thenReturn(0L)

        val dto = UpdateSessionGridDto(shareCode = "AB12", posX = 1, posY = 2, commandType = "ERASE_LETTER", letter = null)
        val result = useCase.execute(dto)

        assertThat(result).isInstanceOf(CocroResult.Success::class.java)
        assertThat((result as CocroResult.Success).value.commandType).isEqualTo("ERASE_LETTER")
    }
}
```

- [ ] **Step 2: Run test to verify**

Run: `TESTCONTAINERS_RYUK_DISABLED=true ./gradlew cocro-bff:test --tests "com.cocro.application.session.usecase.UpdateSessionGridUseCasesTest" --no-daemon`
Expected: 7 tests PASS

- [ ] **Step 3: Commit**

```bash
git add cocro-bff/src/test/kotlin/com/cocro/application/session/usecase/UpdateSessionGridUseCasesTest.kt
git commit -m "[BFF] Add UpdateSessionGridUseCases unit tests (PLACE/ERASE, flush threshold, auth, validation)"
```

---

### Task 3: SubmitGridUseCaseTest

**Files:**
- Create: `cocro-bff/src/test/kotlin/com/cocro/application/grid/usecase/SubmitGridUseCaseTest.kt`

- [ ] **Step 1: Write test file**

```kotlin
package com.cocro.application.grid.usecase

import com.cocro.application.auth.port.CurrentUserProvider
import com.cocro.application.grid.dto.CellDto
import com.cocro.application.grid.dto.SubmitGridDto
import com.cocro.application.grid.port.GridRepository
import com.cocro.application.grid.service.GridIdGenerator
import com.cocro.kernel.auth.enum.Role
import com.cocro.kernel.auth.model.AuthenticatedUser
import com.cocro.kernel.auth.model.valueobject.UserId
import com.cocro.kernel.common.CocroResult
import com.cocro.kernel.grid.enums.CellType
import com.cocro.kernel.grid.enums.GridDifficulty
import com.cocro.kernel.grid.error.GridError
import com.cocro.kernel.grid.model.valueobject.GridShareCode
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class SubmitGridUseCaseTest {

    private val currentUserProvider: CurrentUserProvider = mock()
    private val gridRepository: GridRepository = mock()
    private val gridIdGenerator: GridIdGenerator = mock()

    private val useCase = SubmitGridUseCase(currentUserProvider, gridRepository, gridIdGenerator)

    private val userId = UserId.new()
    private val authenticatedUser = AuthenticatedUser(userId, setOf(Role.PLAYER))

    private fun validDto() = SubmitGridDto(
        title = "Test Grid",
        difficulty = GridDifficulty.EASY,
        reference = null,
        description = null,
        width = 3,
        height = 3,
        cells = listOf(
            CellDto(x = 0, y = 0, type = CellType.LETTER, letter = "A", separator = null, number = null, clues = null),
            CellDto(x = 1, y = 0, type = CellType.LETTER, letter = "B", separator = null, number = null, clues = null),
            CellDto(x = 2, y = 0, type = CellType.BLACK, letter = null, separator = null, number = null, clues = null),
        ),
    )

    @Test
    fun `should submit grid successfully`() {
        val shortId = GridShareCode("GR01")
        whenever(currentUserProvider.currentUserOrNull()).thenReturn(authenticatedUser)
        whenever(gridIdGenerator.generateId()).thenReturn(shortId)
        whenever(gridRepository.findByHashLetters(any())).thenReturn(null)
        whenever(gridRepository.save(any())).thenAnswer { it.arguments[0] }

        val result = useCase.execute(validDto())

        assertThat(result).isInstanceOf(CocroResult.Success::class.java)
        assertThat((result as CocroResult.Success).value).isEqualTo(shortId)
        verify(gridRepository).save(any())
    }

    @Test
    fun `should return UnauthorizedGridCreation when user is not authenticated`() {
        whenever(currentUserProvider.currentUserOrNull()).thenReturn(null)

        val result = useCase.execute(validDto())

        assertThat(result).isInstanceOf(CocroResult.Error::class.java)
        assertThat((result as CocroResult.Error).errors).anyMatch { it is GridError.UnauthorizedGridCreation }
    }

    @Test
    fun `should return DuplicateLetterHash when grid with same hash exists`() {
        val shortId = GridShareCode("GR01")
        whenever(currentUserProvider.currentUserOrNull()).thenReturn(authenticatedUser)
        whenever(gridIdGenerator.generateId()).thenReturn(shortId)
        // Return a grid with same hash
        whenever(gridRepository.findByHashLetters(any())).thenAnswer {
            val grid = mock<com.cocro.kernel.grid.model.Grid>()
            whenever(grid.hashLetters).thenReturn(it.arguments[0] as Long)
            whenever(grid.shortId).thenReturn(GridShareCode("OTHER1"))
            grid
        }

        val result = useCase.execute(validDto())

        assertThat(result).isInstanceOf(CocroResult.Error::class.java)
        assertThat((result as CocroResult.Error).errors).anyMatch { it is GridError.DuplicateLetterHash }
    }

    @Test
    fun `should return validation error when title is missing`() {
        val dto = validDto().copy(title = "")
        whenever(currentUserProvider.currentUserOrNull()).thenReturn(authenticatedUser)

        val result = useCase.execute(dto)

        assertThat(result).isInstanceOf(CocroResult.Error::class.java)
    }
}
```

- [ ] **Step 2: Run test**

Run: `TESTCONTAINERS_RYUK_DISABLED=true ./gradlew cocro-bff:test --tests "com.cocro.application.grid.usecase.SubmitGridUseCaseTest" --no-daemon`
Expected: 4 tests PASS

- [ ] **Step 3: Commit**

```bash
git add cocro-bff/src/test/kotlin/com/cocro/application/grid/usecase/SubmitGridUseCaseTest.kt
git commit -m "[BFF] Add SubmitGridUseCase unit tests (success, auth, duplicate hash, validation)"
```

---

### Task 4: PatchGridUseCaseTest

**Files:**
- Create: `cocro-bff/src/test/kotlin/com/cocro/application/grid/usecase/PatchGridUseCaseTest.kt`

- [ ] **Step 1: Write test file**

```kotlin
package com.cocro.application.grid.usecase

import com.cocro.application.auth.port.CurrentUserProvider
import com.cocro.application.grid.dto.PatchGridDto
import com.cocro.application.grid.port.GridRepository
import com.cocro.kernel.auth.enum.Role
import com.cocro.kernel.auth.model.AuthenticatedUser
import com.cocro.kernel.auth.model.valueobject.UserId
import com.cocro.kernel.common.CocroResult
import com.cocro.kernel.grid.enums.GridDifficulty
import com.cocro.kernel.grid.error.GridError
import com.cocro.kernel.grid.model.Grid
import com.cocro.kernel.grid.model.GridMetadata
import com.cocro.kernel.grid.model.valueobject.GridHeight
import com.cocro.kernel.grid.model.valueobject.GridShareCode
import com.cocro.kernel.grid.model.valueobject.GridTitle
import com.cocro.kernel.grid.model.valueobject.GridWidth
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class PatchGridUseCaseTest {

    private val currentUserProvider: CurrentUserProvider = mock()
    private val gridRepository: GridRepository = mock()

    private val useCase = PatchGridUseCase(currentUserProvider, gridRepository)

    private val authorId = UserId.new()
    private val authorUser = AuthenticatedUser(authorId, setOf(Role.PLAYER))

    private fun existingGrid() = Grid(
        id = java.util.UUID.randomUUID(),
        shortId = GridShareCode("GR01"),
        title = GridTitle("Old Title"),
        metadata = GridMetadata(author = authorId, reference = null, description = null, difficulty = GridDifficulty.EASY),
        width = GridWidth(3),
        height = GridHeight(3),
        cells = emptyList(),
    )

    private fun patchDto() = PatchGridDto(
        gridId = "GR01",
        title = "New Title",
        difficulty = null,
        reference = null,
        description = null,
        width = null,
        height = null,
        cells = null,
    )

    @Test
    fun `should patch grid title successfully`() {
        val grid = existingGrid()
        whenever(gridRepository.findByShortId(GridShareCode("GR01"))).thenReturn(grid)
        whenever(currentUserProvider.currentUserOrNull()).thenReturn(authorUser)
        whenever(gridRepository.findByHashLetters(any())).thenReturn(null)
        whenever(gridRepository.save(any())).thenAnswer { it.arguments[0] }

        val result = useCase.execute(patchDto())

        assertThat(result).isInstanceOf(CocroResult.Success::class.java)
        verify(gridRepository).save(any())
    }

    @Test
    fun `should return GridNotFound when grid does not exist`() {
        whenever(gridRepository.findByShortId(GridShareCode("GR01"))).thenReturn(null)

        val result = useCase.execute(patchDto())

        assertThat(result).isInstanceOf(CocroResult.Error::class.java)
        assertThat((result as CocroResult.Error).errors).anyMatch { it is GridError.GridNotFound }
    }

    @Test
    fun `should return UnauthorizedGridModification when user is not the author`() {
        val grid = existingGrid()
        val otherId = UserId.new()
        val otherUser = AuthenticatedUser(otherId, setOf(Role.PLAYER))
        whenever(gridRepository.findByShortId(GridShareCode("GR01"))).thenReturn(grid)
        whenever(currentUserProvider.currentUserOrNull()).thenReturn(otherUser)

        val result = useCase.execute(patchDto())

        assertThat(result).isInstanceOf(CocroResult.Error::class.java)
        assertThat((result as CocroResult.Error).errors).anyMatch { it is GridError.UnauthorizedGridModification }
    }

    @Test
    fun `should allow admin to patch any grid`() {
        val grid = existingGrid()
        val adminId = UserId.new()
        val adminUser = AuthenticatedUser(adminId, setOf(Role.ADMIN))
        whenever(gridRepository.findByShortId(GridShareCode("GR01"))).thenReturn(grid)
        whenever(currentUserProvider.currentUserOrNull()).thenReturn(adminUser)
        whenever(gridRepository.findByHashLetters(any())).thenReturn(null)
        whenever(gridRepository.save(any())).thenAnswer { it.arguments[0] }

        val result = useCase.execute(patchDto())

        assertThat(result).isInstanceOf(CocroResult.Success::class.java)
    }

    @Test
    fun `should return UnauthorizedGridModification when not authenticated`() {
        val grid = existingGrid()
        whenever(gridRepository.findByShortId(GridShareCode("GR01"))).thenReturn(grid)
        whenever(currentUserProvider.currentUserOrNull()).thenReturn(null)

        val result = useCase.execute(patchDto())

        assertThat(result).isInstanceOf(CocroResult.Error::class.java)
        assertThat((result as CocroResult.Error).errors).anyMatch { it is GridError.UnauthorizedGridModification }
    }
}
```

- [ ] **Step 2: Run test**

Run: `TESTCONTAINERS_RYUK_DISABLED=true ./gradlew cocro-bff:test --tests "com.cocro.application.grid.usecase.PatchGridUseCaseTest" --no-daemon`
Expected: 5 tests PASS

- [ ] **Step 3: Commit**

```bash
git add cocro-bff/src/test/kotlin/com/cocro/application/grid/usecase/PatchGridUseCaseTest.kt
git commit -m "[BFF] Add PatchGridUseCase unit tests (success, not found, auth, admin bypass)"
```

---

## Chunk 2: Enriched Integration Tests

### Task 5: Enrich SessionLifecycleIT with CheckGrid scenario

**Files:**
- Modify: `cocro-bff/src/test/kotlin/com/cocro/integration/SessionLifecycleIT.kt`

- [ ] **Step 1: Add imports for GridCheckSuccess**

Add import for `GridCheckSuccess` to SessionLifecycleIT.

- [ ] **Step 2: Add test — check grid on empty session returns complete=true**

```kotlin
@Test
fun `check grid on empty session returns isComplete=true and isCorrect=true`() {
    val creatorToken = tokenFor()
    val shareCode = post("/api/sessions", CreateSessionDto(gridId = "GRID01"), creatorToken, SessionCreationSuccess::class.java)
        .body!!.shareCode
    post("/api/sessions/start", StartSessionDto(shareCode = shareCode), creatorToken, Any::class.java)

    val checkResp = post("/api/sessions/$shareCode/check", Unit, creatorToken, GridCheckSuccess::class.java)
    assertThat(checkResp.statusCode).isEqualTo(HttpStatus.OK)
    val check = checkResp.body!!
    // GRID01 doesn't exist in the DB — use case should return ReferenceGridNotFound
    // Unless we seed a grid. Since there's no grid seeded, this will be an error response.
}
```

**Note:** This test depends on having a reference grid in MongoDB. We'll need to seed one or adjust the test to verify the error path (ReferenceGridNotFound). The IT should test the REST layer → use case flow. We can test the error path (no reference grid) which is the most likely scenario in tests without grid seeding.

- [ ] **Step 3: Add test — check grid returns ReferenceGridNotFound when grid not in DB**

```kotlin
@Test
fun `check grid returns error when reference grid is not in DB`() {
    val creatorToken = tokenFor()
    val shareCode = post("/api/sessions", CreateSessionDto(gridId = "GRID01"), creatorToken, SessionCreationSuccess::class.java)
        .body!!.shareCode
    post("/api/sessions/start", StartSessionDto(shareCode = shareCode), creatorToken, Any::class.java)

    val checkResp = post("/api/sessions/$shareCode/check", Unit, creatorToken, Any::class.java)
    // ReferenceGridNotFound → mapped to NOT_FOUND (404)
    assertThat(checkResp.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
}
```

- [ ] **Step 4: Add test — get state on non-participant returns error**

```kotlin
@Test
fun `get state returns error when user is not a participant`() {
    val creatorToken = tokenFor()
    val outsiderToken = tokenFor()
    val shareCode = post("/api/sessions", CreateSessionDto(gridId = "GRID01"), creatorToken, SessionCreationSuccess::class.java)
        .body!!.shareCode

    val stateResp = get("/api/sessions/$shareCode/state", outsiderToken, Any::class.java)
    assertThat(stateResp.statusCode).isEqualTo(HttpStatus.FORBIDDEN)
}
```

- [ ] **Step 5: Add test — guest (ANONYMOUS) can join a session**

```kotlin
@Test
fun `guest with ANONYMOUS role can join a session`() {
    val creatorToken = tokenFor()
    val guestToken = tokenFor(roles = setOf(Role.ANONYMOUS))
    val shareCode = post("/api/sessions", CreateSessionDto(gridId = "GRID01"), creatorToken, SessionCreationSuccess::class.java)
        .body!!.shareCode

    val joinResp = post("/api/sessions/join", JoinSessionDto(shareCode = shareCode), guestToken, SessionJoinSuccess::class.java)
    assertThat(joinResp.statusCode).isEqualTo(HttpStatus.OK)
    assertThat(joinResp.body!!.participantCount).isEqualTo(2)
}
```

- [ ] **Step 6: Update tokenFor helper to accept roles parameter**

```kotlin
private fun tokenFor(userId: UserId = UserId.new(), roles: Set<Role> = setOf(Role.PLAYER)): String =
    tokenIssuer.issue(userId, roles)
```

(Already exists with this signature — verify no change needed.)

- [ ] **Step 7: Run all integration tests**

Run: `TESTCONTAINERS_RYUK_DISABLED=true ./gradlew cocro-bff:test --tests "com.cocro.integration.SessionLifecycleIT" --no-daemon`
Expected: All tests PASS

- [ ] **Step 8: Commit**

```bash
git add cocro-bff/src/test/kotlin/com/cocro/integration/SessionLifecycleIT.kt
git commit -m "[BFF] Enrich SessionLifecycleIT — check grid error, state auth, guest join"
```

---

## Chunk 3: Documentation Alignment

### Task 6: Update lifecycle.md — SCORING is planned, not implemented

**Files:**
- Modify: `docs/sessions/lifecycle.md`

- [ ] **Step 1: Read current lifecycle.md**

Read the file and identify sections mentioning SCORING as if implemented.

- [ ] **Step 2: Add a "Planned Features" section**

Move SCORING state details to a "Planned Features" section. Clarify that:
- SCORING transition is not yet implemented (nice-to-have for MVP)
- CheckGridUseCase is read-only — does not trigger state transitions
- Current game flow: CREATING → PLAYING → (manual end not exposed)

- [ ] **Step 3: Commit**

```bash
git add docs/sessions/lifecycle.md
git commit -m "[Docs] Clarify SCORING state is planned, not implemented — update lifecycle.md"
```

---

### Task 7: Verify and commit CheckGridUseCaseTest fix

**Files:**
- Modified: `cocro-bff/src/test/kotlin/com/cocro/application/session/usecase/CheckGridUseCaseTest.kt`

- [ ] **Step 1: Run CheckGridUseCaseTest**

Run: `TESTCONTAINERS_RYUK_DISABLED=true ./gradlew cocro-bff:test --tests "com.cocro.application.session.usecase.CheckGridUseCaseTest" --no-daemon`
Expected: 7 tests PASS

- [ ] **Step 2: Commit the fix**

```bash
git add cocro-bff/src/test/kotlin/com/cocro/application/session/usecase/CheckGridUseCaseTest.kt
git commit -m "[BFF] Fix CheckGridUseCaseTest — update Grid/GridMetadata constructor for new signature"
```

---

## Execution Order

1. Task 7 — Fix & commit CheckGridUseCaseTest (quick fix already done)
2. Task 1 — GetSessionStateUseCaseTest
3. Task 2 — UpdateSessionGridUseCasesTest
4. Task 3 — SubmitGridUseCaseTest
5. Task 4 — PatchGridUseCaseTest
6. Task 5 — Enrich SessionLifecycleIT
7. Task 6 — Documentation alignment
