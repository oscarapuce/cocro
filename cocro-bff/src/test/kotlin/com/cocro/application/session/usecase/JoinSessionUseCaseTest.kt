package com.cocro.application.session.usecase

import com.cocro.application.auth.port.CurrentUserProvider
import com.cocro.application.session.dto.JoinSessionDto
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
import com.cocro.domain.session.enum.SessionStatus
import com.cocro.domain.session.error.SessionError
import com.cocro.domain.session.model.Session
import com.cocro.domain.common.model.Author
import com.cocro.domain.session.model.valueobject.SessionShareCode
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

    private val author = Author(id = UserId.new(), username = "Test")
    private val joiningUserId = UserId.new()
    private val joiningUser = AuthenticatedUser(joiningUserId, "JoiningUser", setOf(Role.PLAYER))
    private val shareCode = SessionShareCode("AB12")
    private val gridId = GridShareCode("GRID01")
    private val session = Session.create(
        author = Author(id = author.id, username = "TestCreator"),
        shareCode = shareCode,
        gridId = gridId,
        gridTemplate = GridTemplateSnapshot(
            shortId = gridId, title = "T", width = 5, height = 5,
            difficulty = null, author = null, reference = null,
            description = null, globalClueLabel = null, globalClueWordLengths = null, cells = emptyList(),
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
        assertThat(success.participantCount).isEqualTo(1)
        assertThat(success.shareCode).isEqualTo("AB12")
        verify(sessionNotifier).broadcast(
            session.shareCode,
            SessionEvent.ParticipantJoined(userId = joiningUserId.toString(), participantCount = 1),
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
        // given — creator has already joined the session
        val sessionWithCreator = session.join(author.id)
        val dto = JoinSessionDto(shareCode = "AB12")
        val creatorUser = AuthenticatedUser(author.id, "TestCreator", setOf(Role.PLAYER))
        whenever(currentUserProvider.currentUserOrNull()).thenReturn(creatorUser)
        whenever(sessionRepository.findByShareCode(shareCode)).thenReturn(sessionWithCreator)
        whenever(sessionGridStateCache.get(sessionWithCreator.id)).thenReturn(null)

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
        val fullSession = (1..4).fold(session) { s, _ -> s.join(UserId.new()) }
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
            author = session.author,
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

    @Test
    fun `should join INTERRUPTED session successfully`() {
        // given — session was interrupted and joiningUser was a LEFT participant
        val sessionWithLeft = session.join(joiningUserId).leave(joiningUserId)
        val interruptedSession = Session.rehydrate(
            id = sessionWithLeft.id,
            shareCode = sessionWithLeft.shareCode,
            author = sessionWithLeft.author,
            gridId = sessionWithLeft.gridId,
            status = SessionStatus.INTERRUPTED,
            participants = sessionWithLeft.participants,
            sessionGridState = sessionWithLeft.sessionGridState,
            createdAt = sessionWithLeft.createdAt,
            updatedAt = sessionWithLeft.updatedAt,
            gridTemplate = sessionWithLeft.gridTemplate,
        )
        val dto = JoinSessionDto(shareCode = "AB12")
        whenever(currentUserProvider.currentUserOrNull()).thenReturn(joiningUser)
        whenever(sessionRepository.findByShareCode(shareCode)).thenReturn(interruptedSession)
        whenever(sessionRepository.save(any())).thenAnswer { it.arguments[0] as Session }
        whenever(sessionGridStateCache.get(interruptedSession.id)).thenReturn(null)

        // when
        val result = useCase.execute(dto)

        // then
        assertThat(result).isInstanceOf(CocroResult.Success::class.java)
        val success = (result as CocroResult.Success).value
        assertThat(success.shareCode).isEqualTo("AB12")
        verify(sessionNotifier).broadcast(
            interruptedSession.shareCode,
            SessionEvent.ParticipantJoined(userId = joiningUserId.toString(), participantCount = 1),
        )
    }

    @Test
    fun `should rejoin session as previously LEFT participant`() {
        // given — session has joiningUserId with status LEFT
        val sessionWithLeft = session.join(joiningUserId).leave(joiningUserId)
        val dto = JoinSessionDto(shareCode = "AB12")
        whenever(currentUserProvider.currentUserOrNull()).thenReturn(joiningUser)
        whenever(sessionRepository.findByShareCode(shareCode)).thenReturn(sessionWithLeft)
        whenever(sessionRepository.save(any())).thenAnswer { it.arguments[0] as Session }
        whenever(sessionGridStateCache.get(sessionWithLeft.id)).thenReturn(null)
        whenever(heartbeatTracker.isAway(sessionWithLeft.id, joiningUserId)).thenReturn(false)

        // when
        val result = useCase.execute(dto)

        // then
        assertThat(result).isInstanceOf(CocroResult.Success::class.java)
        val success = (result as CocroResult.Success).value
        assertThat(success.shareCode).isEqualTo("AB12")
        assertThat(success.participantCount).isEqualTo(1)
        verify(sessionNotifier).broadcast(
            sessionWithLeft.shareCode,
            SessionEvent.ParticipantJoined(userId = joiningUserId.toString(), participantCount = 1),
        )
    }
}
