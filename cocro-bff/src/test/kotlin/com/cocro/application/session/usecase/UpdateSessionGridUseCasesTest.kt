package com.cocro.application.session.usecase

import com.cocro.application.auth.port.CurrentUserProvider
import com.cocro.application.session.dto.UpdateSessionGridDto
import com.cocro.application.session.dto.notification.SessionEvent
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
import com.cocro.kernel.session.model.valueobject.SessionId
import com.cocro.kernel.session.model.valueobject.SessionShareCode
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

/**
 * Fake in-memory cache that avoids Mockito value-class unboxing issues.
 */
private class FakeSessionGridStateCache : SessionGridStateCache {
    private val states = mutableMapOf<String, SessionGridState>()
    private val flushed = mutableMapOf<String, Long>()
    var flushCount = 0
        private set

    override fun get(sessionId: SessionId): SessionGridState? = states[sessionId.toString()]

    override fun compareAndSet(sessionId: SessionId, expectedRevision: Long, newState: SessionGridState): Long {
        states[sessionId.toString()] = newState
        return newState.revision.value
    }

    override fun initialize(sessionId: SessionId, state: SessionGridState) {
        states[sessionId.toString()] = state
    }

    override fun getLastFlushedRevision(sessionId: SessionId): Long = flushed[sessionId.toString()] ?: 0L

    override fun markFlushed(sessionId: SessionId, revision: Long) {
        flushed[sessionId.toString()] = revision
        flushCount++
    }

    override fun getActiveSessions(): Set<SessionId> = emptySet()
}

class UpdateSessionGridUseCasesTest {

    private val currentUserProvider: CurrentUserProvider = mock()
    private val sessionRepository: SessionRepository = mock()
    private val sessionNotifier: SessionNotifier = mock()

    private val creatorId = UserId.new()
    private val authenticatedUser = AuthenticatedUser(creatorId, setOf(Role.PLAYER))
    private val shareCode = SessionShareCode("AB12")
    private val gridId = GridShareCode("GRID01")

    private fun buildPlayingSession(): Session {
        val base = Session.create(creatorId = creatorId, shareCode = shareCode, gridId = gridId)
        return Session.rehydrate(
            id = base.id,
            shareCode = base.shareCode,
            creatorId = base.creatorId,
            gridId = base.gridId,
            status = SessionStatus.PLAYING,
            participants = base.participants,
            sessionGridState = base.sessionGridState,
            createdAt = base.createdAt,
            updatedAt = base.updatedAt,
        )
    }

    private fun setupSession(session: Session, cache: FakeSessionGridStateCache) {
        cache.initialize(session.id, session.sessionGridState)
        whenever(currentUserProvider.currentUserOrNull()).thenReturn(authenticatedUser)
        whenever(sessionRepository.findByShareCode(shareCode)).thenReturn(session)
    }

    @Test
    fun `should place letter successfully and broadcast`() {
        val session = buildPlayingSession()
        val cache = FakeSessionGridStateCache()
        val useCase = UpdateSessionGridUseCases(currentUserProvider, sessionRepository, cache, sessionNotifier, 50L)
        setupSession(session, cache)
        val dto = UpdateSessionGridDto(shareCode = "AB12", posX = 0, posY = 0, commandType = "PLACE_LETTER", letter = 'A')

        val result = useCase.execute(dto)

        assertThat(result).isInstanceOf(CocroResult.Success::class.java)
        val success = (result as CocroResult.Success).value
        assertThat(success.sessionId).isEqualTo(session.id.toString())
        assertThat(success.commandType).isEqualTo("PLACE_LETTER")
        verify(sessionNotifier).broadcast(
            shareCode,
            SessionEvent.GridUpdated(actorId = creatorId.toString(), posX = 0, posY = 0, commandType = "PLACE_LETTER", letter = 'A'),
        )
    }

    @Test
    fun `should flush to MongoDB when threshold is reached`() {
        val session = buildPlayingSession()
        val cache = FakeSessionGridStateCache()
        val useCase = UpdateSessionGridUseCases(currentUserProvider, sessionRepository, cache, sessionNotifier, 1L)
        setupSession(session, cache)
        val dto = UpdateSessionGridDto(shareCode = "AB12", posX = 0, posY = 0, commandType = "PLACE_LETTER", letter = 'A')

        val result = useCase.execute(dto)

        assertThat(result).isInstanceOf(CocroResult.Success::class.java)
        assertThat(cache.flushCount).isEqualTo(1)
    }

    @Test
    fun `should not flush when below threshold`() {
        val session = buildPlayingSession()
        val cache = FakeSessionGridStateCache()
        val useCase = UpdateSessionGridUseCases(currentUserProvider, sessionRepository, cache, sessionNotifier, 50L)
        setupSession(session, cache)
        val dto = UpdateSessionGridDto(shareCode = "AB12", posX = 0, posY = 0, commandType = "PLACE_LETTER", letter = 'A')

        val result = useCase.execute(dto)

        assertThat(result).isInstanceOf(CocroResult.Success::class.java)
        assertThat(cache.flushCount).isEqualTo(0)
    }

    @Test
    fun `should return Unauthorized when user is not authenticated`() {
        whenever(currentUserProvider.currentUserOrNull()).thenReturn(null)
        val cache = FakeSessionGridStateCache()
        val useCase = UpdateSessionGridUseCases(currentUserProvider, sessionRepository, cache, sessionNotifier, 50L)
        val dto = UpdateSessionGridDto(shareCode = "AB12", posX = 0, posY = 0, commandType = "PLACE_LETTER", letter = 'A')

        val result = useCase.execute(dto)

        assertThat(result).isInstanceOf(CocroResult.Error::class.java)
        assertThat((result as CocroResult.Error).errors).contains(SessionError.Unauthorized)
        verifyNoInteractions(sessionRepository)
    }

    @Test
    fun `should return validation error for invalid shareCode`() {
        whenever(currentUserProvider.currentUserOrNull()).thenReturn(authenticatedUser)
        val cache = FakeSessionGridStateCache()
        val useCase = UpdateSessionGridUseCases(currentUserProvider, sessionRepository, cache, sessionNotifier, 50L)
        val dto = UpdateSessionGridDto(shareCode = "bad!", posX = 0, posY = 0, commandType = "PLACE_LETTER", letter = 'A')

        val result = useCase.execute(dto)

        assertThat(result).isInstanceOf(CocroResult.Error::class.java)
        assertThat((result as CocroResult.Error).errors).anyMatch { it is SessionError.InvalidShareCode }
        verifyNoInteractions(sessionRepository)
    }

    @Test
    fun `should return SessionNotFound when session does not exist`() {
        whenever(currentUserProvider.currentUserOrNull()).thenReturn(authenticatedUser)
        whenever(sessionRepository.findByShareCode(shareCode)).thenReturn(null)
        val cache = FakeSessionGridStateCache()
        val useCase = UpdateSessionGridUseCases(currentUserProvider, sessionRepository, cache, sessionNotifier, 50L)
        val dto = UpdateSessionGridDto(shareCode = "AB12", posX = 0, posY = 0, commandType = "PLACE_LETTER", letter = 'A')

        val result = useCase.execute(dto)

        assertThat(result).isInstanceOf(CocroResult.Error::class.java)
        assertThat((result as CocroResult.Error).errors).anyMatch { it is SessionError.SessionNotFound }
    }

    @Test
    fun `should use ERASE_LETTER command type`() {
        val session = buildPlayingSession()
        val cache = FakeSessionGridStateCache()
        val useCase = UpdateSessionGridUseCases(currentUserProvider, sessionRepository, cache, sessionNotifier, 50L)
        setupSession(session, cache)
        val dto = UpdateSessionGridDto(shareCode = "AB12", posX = 0, posY = 0, commandType = "ERASE_LETTER", letter = null)

        val result = useCase.execute(dto)

        assertThat(result).isInstanceOf(CocroResult.Success::class.java)
        assertThat((result as CocroResult.Success).value.commandType).isEqualTo("ERASE_LETTER")
    }
}
