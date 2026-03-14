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
import com.cocro.kernel.session.enum.SessionStatus
import com.cocro.kernel.session.error.SessionError
import com.cocro.kernel.session.model.Session
import com.cocro.kernel.session.model.state.SessionGridCellState
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

    private val useCase = GetSessionStateUseCase(
        currentUserProvider,
        sessionRepository,
        sessionGridStateCache,
    )

    private val creatorId = UserId.new()
    private val participantId = UserId.new()
    private val shareCode = SessionShareCode("AB12")
    private val gridId = GridShareCode("GRID01")

    private fun buildPlayingSessionWithParticipant(): Session {
        val base = Session.create(creatorId = creatorId, shareCode = shareCode, gridId = gridId)
        val withParticipant = base.join(participantId)
        return Session.rehydrate(
            id = base.id, shareCode = base.shareCode, creatorId = base.creatorId,
            gridId = base.gridId, status = SessionStatus.PLAYING,
            participants = withParticipant.participants,
            sessionGridState = base.sessionGridState,
            createdAt = base.createdAt, updatedAt = base.updatedAt,
        )
    }

    @Test
    fun `should return session state from cache when available`() {
        // given
        val user = AuthenticatedUser(participantId, setOf(Role.PLAYER))
        val session = buildPlayingSessionWithParticipant()
        val cachedState = session.sessionGridState.copy(
            cells = mapOf(CellPos(0, 0) to SessionGridCellState.Letter('A')),
        )
        whenever(currentUserProvider.currentUserOrNull()).thenReturn(user)
        whenever(sessionRepository.findByShareCode(shareCode)).thenReturn(session)
        whenever(sessionGridStateCache.get(session.id)).thenReturn(cachedState)

        // when
        val result = useCase.execute(shareCode.value)

        // then
        assertThat(result).isInstanceOf(CocroResult.Success::class.java)
        val dto = (result as CocroResult.Success).value
        assertThat(dto.cells).hasSize(1)
        assertThat(dto.cells[0].x).isEqualTo(0)
        assertThat(dto.cells[0].y).isEqualTo(0)
        assertThat(dto.cells[0].letter).isEqualTo('A')
    }

    @Test
    fun `should fall back to embedded state when cache miss`() {
        // given
        val user = AuthenticatedUser(participantId, setOf(Role.PLAYER))
        val session = buildPlayingSessionWithParticipant()
        whenever(currentUserProvider.currentUserOrNull()).thenReturn(user)
        whenever(sessionRepository.findByShareCode(shareCode)).thenReturn(session)
        whenever(sessionGridStateCache.get(session.id)).thenReturn(null)

        // when
        val result = useCase.execute(shareCode.value)

        // then
        assertThat(result).isInstanceOf(CocroResult.Success::class.java)
        val dto = (result as CocroResult.Success).value
        assertThat(dto.revision).isEqualTo(0L)
        assertThat(dto.cells).isEmpty()
    }

    @Test
    fun `should return Unauthorized when user is not authenticated`() {
        // given
        whenever(currentUserProvider.currentUserOrNull()).thenReturn(null)

        // when
        val result = useCase.execute(shareCode.value)

        // then
        assertThat(result).isInstanceOf(CocroResult.Error::class.java)
        val errors = (result as CocroResult.Error).errors
        assertThat(errors).contains(SessionError.Unauthorized)
        verifyNoInteractions(sessionRepository)
    }

    @Test
    fun `should return InvalidShareCode when shareCode format is invalid`() {
        // given
        val user = AuthenticatedUser(participantId, setOf(Role.PLAYER))
        whenever(currentUserProvider.currentUserOrNull()).thenReturn(user)

        // when
        val result = useCase.execute("bad!")

        // then
        assertThat(result).isInstanceOf(CocroResult.Error::class.java)
        val errors = (result as CocroResult.Error).errors
        assertThat(errors).anyMatch { it is SessionError.InvalidShareCode }
        verifyNoInteractions(sessionRepository)
    }

    @Test
    fun `should return SessionNotFound when session does not exist`() {
        // given
        val user = AuthenticatedUser(participantId, setOf(Role.PLAYER))
        whenever(currentUserProvider.currentUserOrNull()).thenReturn(user)
        whenever(sessionRepository.findByShareCode(shareCode)).thenReturn(null)

        // when
        val result = useCase.execute(shareCode.value)

        // then
        assertThat(result).isInstanceOf(CocroResult.Error::class.java)
        val errors = (result as CocroResult.Error).errors
        assertThat(errors).anyMatch { it is SessionError.SessionNotFound }
    }

    @Test
    fun `should return UserNotParticipant when user is not a joined participant`() {
        // given
        val outsiderId = UserId.new()
        val user = AuthenticatedUser(outsiderId, setOf(Role.PLAYER))
        val session = buildPlayingSessionWithParticipant()
        whenever(currentUserProvider.currentUserOrNull()).thenReturn(user)
        whenever(sessionRepository.findByShareCode(shareCode)).thenReturn(session)

        // when
        val result = useCase.execute(shareCode.value)

        // then
        assertThat(result).isInstanceOf(CocroResult.Error::class.java)
        val errors = (result as CocroResult.Error).errors
        assertThat(errors).anyMatch { it is SessionError.UserNotParticipant }
        verifyNoInteractions(sessionGridStateCache)
    }
}
