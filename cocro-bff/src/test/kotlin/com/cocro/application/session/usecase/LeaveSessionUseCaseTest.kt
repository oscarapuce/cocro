package com.cocro.application.session.usecase

import com.cocro.application.auth.port.CurrentUserProvider
import com.cocro.application.session.dto.LeaveSessionDto
import com.cocro.application.session.port.HeartbeatTracker
import com.cocro.application.session.port.SessionGridStateCache
import com.cocro.application.session.port.SessionNotifier
import com.cocro.application.session.port.SessionRepository
import com.cocro.domain.auth.enum.Role
import com.cocro.domain.auth.model.AuthenticatedUser
import com.cocro.domain.auth.model.valueobject.UserId
import com.cocro.domain.common.CocroResult
import com.cocro.domain.grid.model.GridTemplateSnapshot
import com.cocro.domain.grid.model.valueobject.GridShareCode
import com.cocro.domain.session.error.SessionError
import com.cocro.domain.session.model.Session
import com.cocro.domain.session.model.valueobject.SessionShareCode
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import com.cocro.application.session.dto.notification.SessionEvent
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class LeaveSessionUseCaseTest {

    private val currentUserProvider: CurrentUserProvider = mock()
    private val sessionRepository: SessionRepository = mock()
    private val sessionGridStateCache: SessionGridStateCache = mock()
    private val sessionNotifier: SessionNotifier = mock()
    private val heartbeatTracker: HeartbeatTracker = mock()

    private val useCase = LeaveSessionUseCase(currentUserProvider, sessionRepository, sessionGridStateCache, sessionNotifier, heartbeatTracker)

    private val creatorId = UserId.new()
    private val participantId = UserId.new()
    private val participantUser = AuthenticatedUser(participantId, setOf(Role.PLAYER))
    private val shareCode = SessionShareCode("AB12")
    private val gridId = GridShareCode("GRID01")
    private val session = Session.create(
        creatorId = creatorId,
        shareCode = shareCode,
        gridId = gridId,
        gridTemplate = GridTemplateSnapshot(
            shortId = gridId, title = "T", width = 5, height = 5,
            difficulty = null, author = null, reference = null,
            description = null, globalClueLabel = null,
            globalClueWordLengths = null, cells = emptyList(),
        ),
    ).join(participantId)

    @Test
    fun `should leave session successfully`() {
        // given
        val dto = LeaveSessionDto(shareCode = "AB12")
        whenever(currentUserProvider.currentUserOrNull()).thenReturn(participantUser)
        whenever(sessionRepository.findByShareCode(shareCode)).thenReturn(session)
        whenever(sessionRepository.save(any())).thenAnswer { it.arguments[0] as Session }

        // when
        val result = useCase.execute(dto)

        // then
        assertThat(result).isInstanceOf(CocroResult.Success::class.java)
        val success = (result as CocroResult.Success).value
        assertThat(success.sessionId).isEqualTo(session.id.toString())
        verify(sessionNotifier).broadcast(
            session.shareCode,
            SessionEvent.ParticipantLeft(userId = participantId.toString(), participantCount = 0, reason = "explicit"),
        )
    }

    @Test
    fun `should return error when user is not authenticated`() {
        // given
        val dto = LeaveSessionDto(shareCode = "AB12")
        whenever(currentUserProvider.currentUserOrNull()).thenReturn(null)

        // when
        val result = useCase.execute(dto)

        // then
        assertThat(result).isInstanceOf(CocroResult.Error::class.java)
        val errors = (result as CocroResult.Error).errors
        assertThat(errors).contains(SessionError.Unauthorized)
        verifyNoInteractions(sessionRepository)
    }

    @Test
    fun `should return error when shareCode format is invalid`() {
        // given
        val dto = LeaveSessionDto(shareCode = "bad!")
        whenever(currentUserProvider.currentUserOrNull()).thenReturn(participantUser)

        // when
        val result = useCase.execute(dto)

        // then
        assertThat(result).isInstanceOf(CocroResult.Error::class.java)
        val errors = (result as CocroResult.Error).errors
        assertThat(errors).anyMatch { it is SessionError.InvalidShareCode }
        verifyNoInteractions(sessionRepository)
    }

    @Test
    fun `should return error when session is not found`() {
        // given
        val dto = LeaveSessionDto(shareCode = "AB12")
        whenever(currentUserProvider.currentUserOrNull()).thenReturn(participantUser)
        whenever(sessionRepository.findByShareCode(shareCode)).thenReturn(null)

        // when
        val result = useCase.execute(dto)

        // then
        assertThat(result).isInstanceOf(CocroResult.Error::class.java)
        val errors = (result as CocroResult.Error).errors
        assertThat(errors).anyMatch { it is SessionError.SessionNotFound }
    }

    @Test
    fun `should return error when user is not a participant`() {
        // given
        val outsider = AuthenticatedUser(UserId.new(), setOf(Role.PLAYER))
        val dto = LeaveSessionDto(shareCode = "AB12")
        whenever(currentUserProvider.currentUserOrNull()).thenReturn(outsider)
        whenever(sessionRepository.findByShareCode(shareCode)).thenReturn(session)

        // when
        val result = useCase.execute(dto)

        // then
        assertThat(result).isInstanceOf(CocroResult.Error::class.java)
        val errors = (result as CocroResult.Error).errors
        assertThat(errors).anyMatch { it is SessionError.UserNotParticipant }
        verify(sessionRepository, never()).save(any())
    }

    @Test
    fun `should broadcast SessionInterrupted when last participant leaves`() {
        // given
        val dto = LeaveSessionDto(shareCode = "AB12")
        whenever(currentUserProvider.currentUserOrNull()).thenReturn(participantUser)
        whenever(sessionRepository.findByShareCode(shareCode)).thenReturn(session)
        whenever(sessionRepository.save(any())).thenAnswer { it.arguments[0] as Session }

        // when
        val result = useCase.execute(dto)

        // then
        assertThat(result).isInstanceOf(CocroResult.Success::class.java)
        verify(sessionNotifier).broadcast(
            session.shareCode,
            SessionEvent.ParticipantLeft(userId = participantId.toString(), participantCount = 0, reason = "explicit"),
        )
        verify(sessionNotifier).broadcast(
            session.shareCode,
            SessionEvent.SessionInterrupted(shareCode = session.shareCode.toString()),
        )
    }
}
