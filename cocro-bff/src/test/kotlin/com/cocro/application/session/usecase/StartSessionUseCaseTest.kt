package com.cocro.application.session.usecase

import com.cocro.application.auth.port.CurrentUserProvider
import com.cocro.application.session.dto.StartSessionDto
import com.cocro.application.session.port.SessionNotifier
import com.cocro.application.session.port.SessionRepository
import com.cocro.kernel.auth.enum.Role
import com.cocro.kernel.auth.model.AuthenticatedUser
import com.cocro.kernel.auth.model.valueobject.UserId
import com.cocro.kernel.common.CocroResult
import com.cocro.kernel.grid.model.GridTemplateSnapshot
import com.cocro.kernel.grid.model.valueobject.GridShareCode
import com.cocro.kernel.session.enum.InviteStatus
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

class StartSessionUseCaseTest {

    private val currentUserProvider: CurrentUserProvider = mock()
    private val sessionRepository: SessionRepository = mock()
    private val sessionNotifier: SessionNotifier = mock()

    private val useCase = StartSessionUseCase(currentUserProvider, sessionRepository, sessionNotifier)

    private val creatorId = UserId.new()
    private val creatorUser = AuthenticatedUser(creatorId, setOf(Role.PLAYER))
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
    fun `should start session successfully`() {
        // given
        val dto = StartSessionDto(shareCode = "AB12")
        whenever(currentUserProvider.currentUserOrNull()).thenReturn(creatorUser)
        whenever(sessionRepository.findByShareCode(shareCode)).thenReturn(session)
        whenever(sessionRepository.save(any())).thenAnswer { it.arguments[0] as Session }

        // when
        val result = useCase.execute(dto)

        // then
        assertThat(result).isInstanceOf(CocroResult.Success::class.java)
        val success = (result as CocroResult.Success).value
        assertThat(success.participantCount).isEqualTo(1)
    }

    @Test
    fun `should return error when user is not authenticated`() {
        // given
        val dto = StartSessionDto(shareCode = "AB12")
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
        val dto = StartSessionDto(shareCode = "bad!")
        whenever(currentUserProvider.currentUserOrNull()).thenReturn(creatorUser)

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
        val dto = StartSessionDto(shareCode = "AB12")
        whenever(currentUserProvider.currentUserOrNull()).thenReturn(creatorUser)
        whenever(sessionRepository.findByShareCode(shareCode)).thenReturn(null)

        // when
        val result = useCase.execute(dto)

        // then
        assertThat(result).isInstanceOf(CocroResult.Error::class.java)
        val errors = (result as CocroResult.Error).errors
        assertThat(errors).anyMatch { it is SessionError.SessionNotFound }
    }

    @Test
    fun `should return error when session is in ENDED status`() {
        // given
        val dto = StartSessionDto(shareCode = "AB12")
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
        )
        whenever(currentUserProvider.currentUserOrNull()).thenReturn(creatorUser)
        whenever(sessionRepository.findByShareCode(shareCode)).thenReturn(endedSession)

        // when
        val result = useCase.execute(dto)

        // then
        assertThat(result).isInstanceOf(CocroResult.Error::class.java)
        val errors = (result as CocroResult.Error).errors
        assertThat(errors).anyMatch { it is SessionError.InvalidStatusForAction }
    }

    @Test
    fun `should return error when session has no active participants`() {
        // given
        val dto = StartSessionDto(shareCode = "AB12")
        val emptySession = Session.rehydrate(
            id = session.id,
            shareCode = session.shareCode,
            creatorId = session.creatorId,
            gridId = session.gridId,
            status = SessionStatus.PLAYING,
            participants = session.participants.map { it.copy(status = InviteStatus.LEFT) },
            sessionGridState = session.sessionGridState,
            createdAt = session.createdAt,
            updatedAt = session.updatedAt,
        )
        whenever(currentUserProvider.currentUserOrNull()).thenReturn(creatorUser)
        whenever(sessionRepository.findByShareCode(shareCode)).thenReturn(emptySession)

        // when
        val result = useCase.execute(dto)

        // then
        assertThat(result).isInstanceOf(CocroResult.Error::class.java)
        val errors = (result as CocroResult.Error).errors
        assertThat(errors).contains(SessionError.NotEnoughParticipants)
    }
}
