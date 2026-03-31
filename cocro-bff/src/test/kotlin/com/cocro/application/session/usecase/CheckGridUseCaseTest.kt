package com.cocro.application.session.usecase

import com.cocro.application.auth.port.CurrentUserProvider
import com.cocro.application.grid.port.GridRepository
import com.cocro.application.session.dto.notification.SessionEvent
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
import com.cocro.domain.session.model.state.GridCheckResult
import com.cocro.domain.session.model.state.SessionGridState
import com.cocro.domain.session.model.valueobject.SessionShareCode
import com.cocro.domain.grid.model.Grid
import com.cocro.domain.grid.model.GridMetadata
import com.cocro.domain.grid.model.valueobject.GridDimension
import com.cocro.domain.grid.model.valueobject.GridHeight
import com.cocro.domain.grid.model.valueobject.GridTitle
import com.cocro.domain.grid.model.valueobject.GridWidth
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.argThat
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import java.util.UUID

class CheckGridUseCaseTest {

    private val currentUserProvider: CurrentUserProvider = mock()
    private val sessionRepository: SessionRepository = mock()
    private val sessionGridStateCache: SessionGridStateCache = mock()
    private val gridRepository: GridRepository = mock()
    private val sessionNotifier: SessionNotifier = mock()

    private val useCase = CheckGridUseCase(currentUserProvider, sessionRepository, sessionGridStateCache, gridRepository, sessionNotifier)

    private val author = Author(id = UserId.new(), username = "Test")
    private val participantId = UserId.new()
    private val participantUser = AuthenticatedUser(participantId, "Participant", setOf(Role.PLAYER))
    private val shareCode = SessionShareCode("AB12")
    private val gridId = GridShareCode("GRID01")

    private fun minimalSnapshot() = GridTemplateSnapshot(
        shortId = gridId,
        title = "T", width = 5, height = 5,
        difficulty = null, author = null, reference = null,
        description = null, globalClueLabel = null, globalClueWordLengths = null,
        cells = emptyList(),
    )

    private fun baseSession() = Session.create(
        author = Author(id = author.id, username = "TestCreator"),
        shareCode = shareCode,
        gridId = gridId,
        gridTemplate = minimalSnapshot(),
    )

    private val playingSession: Session by lazy {
        val base = baseSession()
        Session.rehydrate(
            id = base.id,
            shareCode = shareCode,
            author = Author(id = author.id, username = "TestCreator"),
            gridId = gridId,
            status = SessionStatus.PLAYING,
            participants = base.join(participantId).participants,
            sessionGridState = base.sessionGridState,
            createdAt = base.createdAt,
            updatedAt = base.updatedAt,
        )
    }

    // Build a minimal Grid with no LetterCells so checkAgainst returns totalCount=0, filledCount=0
    private val referenceGrid = Grid(
        id = UUID.randomUUID(),
        shortId = gridId,
        metadata = GridMetadata(
            title = GridTitle("Test Grid"),
            author = Author(id = author.id, username = "TestCreator"),
            reference = null,
            description = null,
            difficulty = "NONE",
        ),
        dimension = GridDimension(width = GridWidth(5), height = GridHeight(5)),
        cells = emptyList(),
    )

    private fun buildPlayingSession(): Session {
        val base = Session.create(author = Author(id = author.id, username = "TestCreator"), shareCode = shareCode, gridId = gridId, gridTemplate = minimalSnapshot())
        val withParticipant = base.join(participantId)
        return Session.rehydrate(
            id = base.id,
            shareCode = base.shareCode,
            author = base.author,
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
        assertThat(success.wrongCount).isEqualTo(0)
        assertThat(success.correctCount).isEqualTo(0)
        verify(sessionRepository).updateGridState(session.id, session.sessionGridState)
        verify(sessionNotifier).broadcast(
            session.shareCode,
            SessionEvent.GridChecked(
                userId = participantId.toString(),
                isComplete = true,
                isCorrect = true,
                correctCount = 0,
                totalCount = 0,
            ),
        )
    }

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

        // then
        assertThat(result).isInstanceOf(CocroResult.Success::class.java)
        val success = (result as CocroResult.Success).value
        assertThat(success.isComplete).isTrue()
        assertThat(success.isCorrect).isTrue()
        verify(sessionRepository, atLeastOnce()).save(argThat<Session> { status == SessionStatus.ENDED })
        verify(sessionNotifier).broadcast(
            session.shareCode,
            SessionEvent.SessionEnded(shareCode = "AB12", correctCount = 0, totalCount = 0),
        )
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
        val outsider = AuthenticatedUser(UserId.new(), "TestUser", setOf(Role.PLAYER))
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
        val base = Session.create(author = Author(id = author.id, username = "TestCreator"), shareCode = shareCode, gridId = gridId, gridTemplate = minimalSnapshot())
        val withParticipant = base.join(participantId)
        // Session in ENDED status (not PLAYING, so InvalidStatusForAction is expected)
        val endedSession = Session.rehydrate(
            id = base.id,
            shareCode = base.shareCode,
            author = base.author,
            gridId = base.gridId,
            status = SessionStatus.ENDED,
            participants = withParticipant.participants,
            sessionGridState = base.sessionGridState,
            createdAt = base.createdAt,
            updatedAt = base.updatedAt,
        )
        whenever(currentUserProvider.currentUserOrNull()).thenReturn(participantUser)
        whenever(sessionRepository.findByShareCode(shareCode)).thenReturn(endedSession)

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
