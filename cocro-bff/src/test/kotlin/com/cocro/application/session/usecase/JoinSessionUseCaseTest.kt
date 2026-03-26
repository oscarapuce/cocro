package com.cocro.application.session.usecase

import com.cocro.application.auth.port.CurrentUserProvider
import com.cocro.application.session.dto.JoinSessionDto
import com.cocro.application.session.port.HeartbeatTracker
import com.cocro.application.session.port.SessionGridStateCache
import com.cocro.application.session.port.SessionNotifier
import com.cocro.application.session.port.SessionRepository
import com.cocro.kernel.auth.enum.Role
import com.cocro.kernel.auth.model.AuthenticatedUser
import com.cocro.kernel.auth.model.valueobject.UserId
import com.cocro.kernel.common.CocroResult
import com.cocro.kernel.grid.model.GridTemplateSnapshot
import com.cocro.kernel.grid.model.valueobject.GridShareCode
import com.cocro.kernel.session.enum.SessionStatus
import com.cocro.kernel.session.error.SessionError
import com.cocro.kernel.session.model.Session
import com.cocro.kernel.session.model.valueobject.SessionShareCode
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import com.cocro.application.session.dto.notification.SessionEvent
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class JoinSessionUseCaseTest {

    private val currentUserProvider: CurrentUserProvider = mock()
    private val sessionRepository: SessionRepository = mock()
    private val sessionGridStateCache: SessionGridStateCache = mock()
    private val sessionNotifier: SessionNotifier = mock()
    private val heartbeatTracker: HeartbeatTracker = mock()

    private val useCase = JoinSessionUseCase(currentUserProvider, sessionRepository, sessionGridStateCache, sessionNotifier, heartbeatTracker)

    private val creatorId = UserId.new()
    private val joiningUserId = UserId.new()
    private val joiningUser = AuthenticatedUser(joiningUserId, setOf(Role.PLAYER))
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
    )

    @Test
    fun `should join session successfully`() {
        // given
        val dto = JoinSessionDto(shareCode = "AB12")
        whenever(currentUserProvider.currentUserOrNull()).thenReturn(joiningUser)
        whenever(sessionRepository.findByShareCode(shareCode)).thenReturn(session)
        whenever(sessionRepository.save(any())).thenAnswer { it.arguments[0] as Session }
        whenever(sessionGridStateCache.get(session.id)).thenReturn(null)

        // when
        val result = useCase.execute(dto)

        // then
        assertThat(result).isInstanceOf(CocroResult.Success::class.java)
        val success = (result as CocroResult.Success).value
        assertThat(success.participantCount).isEqualTo(2)
        assertThat(success.shareCode).isEqualTo("AB12")
        verify(sessionNotifier).broadcast(
            session.shareCode,
            SessionEvent.ParticipantJoined(userId = joiningUserId.toString(), participantCount = 2),
        )
    }

    @Test
    fun `should return error when user is not authenticated`() {
        // given
        val dto = JoinSessionDto(shareCode = "AB12")
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
        val dto = JoinSessionDto(shareCode = "bad!")
        whenever(currentUserProvider.currentUserOrNull()).thenReturn(joiningUser)

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
        val dto = JoinSessionDto(shareCode = "AB12")
        whenever(currentUserProvider.currentUserOrNull()).thenReturn(joiningUser)
        whenever(sessionRepository.findByShareCode(shareCode)).thenReturn(null)

        // when
        val result = useCase.execute(dto)

        // then
        assertThat(result).isInstanceOf(CocroResult.Error::class.java)
        val errors = (result as CocroResult.Error).errors
        assertThat(errors).anyMatch { it is SessionError.SessionNotFound }
    }

    @Test
    fun `should return SessionFullDto when active participant joins again (idempotent)`() {
        // given — creator is already a JOINED participant
        val dto = JoinSessionDto(shareCode = "AB12")
        val creatorUser = AuthenticatedUser(creatorId, setOf(Role.PLAYER))
        whenever(currentUserProvider.currentUserOrNull()).thenReturn(creatorUser)
        whenever(sessionRepository.findByShareCode(shareCode)).thenReturn(session)
        whenever(sessionGridStateCache.get(session.id)).thenReturn(null)

        // when
        val result = useCase.execute(dto)

        // then — idempotent: returns full dto instead of error
        assertThat(result).isInstanceOf(CocroResult.Success::class.java)
        val success = (result as CocroResult.Success).value
        assertThat(success.shareCode).isEqualTo("AB12")
    }

    @Test
    fun `should return error when session is full`() {
        // given
        val dto = JoinSessionDto(shareCode = "AB12")
        val fullSession = (2..4).fold(session) { s, _ -> s.join(UserId.new()) }
        whenever(currentUserProvider.currentUserOrNull()).thenReturn(joiningUser)
        whenever(sessionRepository.findByShareCode(shareCode)).thenReturn(fullSession)

        // when
        val result = useCase.execute(dto)

        // then
        assertThat(result).isInstanceOf(CocroResult.Error::class.java)
        val errors = (result as CocroResult.Error).errors
        assertThat(errors).contains(SessionError.SessionFull)
    }

    @Test
    fun `should return error when session status is ENDED`() {
        // given
        val dto = JoinSessionDto(shareCode = "AB12")
        val endedSession = Session.rehydrate(
            id = session.id,
            shareCode = session.shareCode,
            creatorId = session.creatorId,
            gridId = session.gridId,
            status = SessionStatus.ENDED,
            participants = session.participants,
            sessionGridState = session.sessionGridState,
            createdAt = session.createdAt,
            updatedAt = session.updatedAt,
            gridTemplate = session.gridTemplate,
        )
        whenever(currentUserProvider.currentUserOrNull()).thenReturn(joiningUser)
        whenever(sessionRepository.findByShareCode(shareCode)).thenReturn(endedSession)

        // when
        val result = useCase.execute(dto)

        // then
        assertThat(result).isInstanceOf(CocroResult.Error::class.java)
        val errors = (result as CocroResult.Error).errors
        assertThat(errors).anyMatch { it is SessionError.InvalidStatusForAction }
    }
}
