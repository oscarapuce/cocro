package com.cocro.application.session.usecase

import com.cocro.application.auth.port.CurrentUserProvider
import com.cocro.application.grid.port.GridRepository
import com.cocro.application.session.port.SessionGridStateCache
import com.cocro.application.session.port.SessionRepository
import com.cocro.kernel.auth.enum.Role
import com.cocro.kernel.auth.model.AuthenticatedUser
import com.cocro.kernel.auth.model.valueobject.UserId
import com.cocro.kernel.common.CocroResult
import com.cocro.kernel.grid.model.valueobject.GridShareCode
import com.cocro.kernel.session.enum.SessionStatus
import com.cocro.kernel.session.error.SessionError
import com.cocro.kernel.session.model.Session
import com.cocro.kernel.session.model.state.GridCheckResult
import com.cocro.kernel.session.model.state.SessionGridState
import com.cocro.kernel.session.model.valueobject.SessionShareCode
import com.cocro.kernel.grid.model.Grid
import com.cocro.kernel.grid.model.GridMetadata
import com.cocro.kernel.grid.model.valueobject.GridHeight
import com.cocro.kernel.grid.model.valueobject.GridTitle
import com.cocro.kernel.grid.model.valueobject.GridWidth
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import java.util.UUID

class CheckGridUseCaseTest {

    private val currentUserProvider: CurrentUserProvider = mock()
    private val sessionRepository: SessionRepository = mock()
    private val sessionGridStateCache: SessionGridStateCache = mock()
    private val gridRepository: GridRepository = mock()

    private val useCase = CheckGridUseCase(currentUserProvider, sessionRepository, sessionGridStateCache, gridRepository)

    private val creatorId = UserId.new()
    private val participantId = UserId.new()
    private val participantUser = AuthenticatedUser(participantId, setOf(Role.PLAYER))
    private val shareCode = SessionShareCode("AB12")
    private val gridId = GridShareCode("GRID01")

    private val playingSession = Session.rehydrate(
        id = Session.create(creatorId = creatorId, shareCode = shareCode, gridId = gridId).id,
        shareCode = shareCode,
        creatorId = creatorId,
        gridId = gridId,
        status = SessionStatus.PLAYING,
        participants = Session.create(creatorId = creatorId, shareCode = shareCode, gridId = gridId)
            .join(participantId).participants,
        sessionGridState = Session.create(creatorId = creatorId, shareCode = shareCode, gridId = gridId).sessionGridState,
        createdAt = Session.create(creatorId = creatorId, shareCode = shareCode, gridId = gridId).createdAt,
        updatedAt = Session.create(creatorId = creatorId, shareCode = shareCode, gridId = gridId).updatedAt,
    )

    // Build a minimal Grid with no LetterCells so checkAgainst returns totalCount=0, filledCount=0
    private val referenceGrid = Grid(
        id = UUID.randomUUID(),
        shortId = gridId,
        title = GridTitle("Test Grid"),
        metadata = GridMetadata(
            author = creatorId,
            reference = null,
            description = null,
            difficulty = "NONE",
        ),
        width = GridWidth(5),
        height = GridHeight(5),
        cells = emptyList(),
    )

    private fun buildPlayingSession(): Session {
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
    fun `should return GridCheckSuccess when participant checks a PLAYING session`() {
        // given
        val session = buildPlayingSession()
        whenever(currentUserProvider.currentUserOrNull()).thenReturn(participantUser)
        whenever(sessionRepository.findByShareCode(shareCode)).thenReturn(session)
        whenever(sessionGridStateCache.get(session.id)).thenReturn(null)
        whenever(gridRepository.findByShortId(session.sessionGridState.gridShareCode)).thenReturn(referenceGrid)

        // when
        val result = useCase.execute("AB12")

        // then
        assertThat(result).isInstanceOf(CocroResult.Success::class.java)
        val success = (result as CocroResult.Success).value
        assertThat(success.shareCode).isEqualTo("AB12")
        assertThat(success.isComplete).isTrue()
        assertThat(success.isCorrect).isTrue()
        assertThat(success.filledCount).isEqualTo(0)
        assertThat(success.totalCount).isEqualTo(0)
    }

    @Test
    fun `should return Unauthorized when user is not authenticated`() {
        // given
        whenever(currentUserProvider.currentUserOrNull()).thenReturn(null)

        // when
        val result = useCase.execute("AB12")

        // then
        assertThat(result).isInstanceOf(CocroResult.Error::class.java)
        val errors = (result as CocroResult.Error).errors
        assertThat(errors).contains(SessionError.Unauthorized)
        verifyNoInteractions(sessionRepository)
    }

    @Test
    fun `should return InvalidShareCode when shareCode format is invalid`() {
        // given
        whenever(currentUserProvider.currentUserOrNull()).thenReturn(participantUser)

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
        whenever(currentUserProvider.currentUserOrNull()).thenReturn(participantUser)
        whenever(sessionRepository.findByShareCode(shareCode)).thenReturn(null)

        // when
        val result = useCase.execute("AB12")

        // then
        assertThat(result).isInstanceOf(CocroResult.Error::class.java)
        val errors = (result as CocroResult.Error).errors
        assertThat(errors).anyMatch { it is SessionError.SessionNotFound }
    }

    @Test
    fun `should return UserNotParticipant when user is not in the session`() {
        // given
        val outsider = AuthenticatedUser(UserId.new(), setOf(Role.PLAYER))
        val session = buildPlayingSession()
        whenever(currentUserProvider.currentUserOrNull()).thenReturn(outsider)
        whenever(sessionRepository.findByShareCode(shareCode)).thenReturn(session)

        // when
        val result = useCase.execute("AB12")

        // then
        assertThat(result).isInstanceOf(CocroResult.Error::class.java)
        val errors = (result as CocroResult.Error).errors
        assertThat(errors).anyMatch { it is SessionError.UserNotParticipant }
    }

    @Test
    fun `should return InvalidStatusForAction when session is not PLAYING`() {
        // given
        val base = Session.create(creatorId = creatorId, shareCode = shareCode, gridId = gridId)
        val withParticipant = base.join(participantId)
        // Session in CREATING status (the default after Session.create)
        val creatingSession = Session.rehydrate(
            id = base.id,
            shareCode = base.shareCode,
            creatorId = base.creatorId,
            gridId = base.gridId,
            status = SessionStatus.CREATING,
            participants = withParticipant.participants,
            sessionGridState = base.sessionGridState,
            createdAt = base.createdAt,
            updatedAt = base.updatedAt,
        )
        whenever(currentUserProvider.currentUserOrNull()).thenReturn(participantUser)
        whenever(sessionRepository.findByShareCode(shareCode)).thenReturn(creatingSession)

        // when
        val result = useCase.execute("AB12")

        // then
        assertThat(result).isInstanceOf(CocroResult.Error::class.java)
        val errors = (result as CocroResult.Error).errors
        assertThat(errors).anyMatch { it is SessionError.InvalidStatusForAction }
    }

    @Test
    fun `should return ReferenceGridNotFound when reference grid does not exist`() {
        // given
        val session = buildPlayingSession()
        whenever(currentUserProvider.currentUserOrNull()).thenReturn(participantUser)
        whenever(sessionRepository.findByShareCode(shareCode)).thenReturn(session)
        whenever(sessionGridStateCache.get(session.id)).thenReturn(null)
        whenever(gridRepository.findByShortId(session.sessionGridState.gridShareCode)).thenReturn(null)

        // when
        val result = useCase.execute("AB12")

        // then
        assertThat(result).isInstanceOf(CocroResult.Error::class.java)
        val errors = (result as CocroResult.Error).errors
        assertThat(errors).anyMatch { it is SessionError.ReferenceGridNotFound }
    }
}
