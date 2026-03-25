package com.cocro.application.session.usecase

import com.cocro.application.auth.port.CurrentUserProvider
import com.cocro.application.grid.port.GridRepository
import com.cocro.application.session.port.SessionRepository
import com.cocro.kernel.auth.enum.Role
import com.cocro.kernel.auth.model.AuthenticatedUser
import com.cocro.kernel.auth.model.valueobject.UserId
import com.cocro.kernel.common.CocroResult
import com.cocro.kernel.grid.enums.ClueDirection
import com.cocro.kernel.grid.enums.SeparatorType
import com.cocro.kernel.grid.model.Cell
import com.cocro.kernel.grid.model.CellPos
import com.cocro.kernel.grid.model.Clue
import com.cocro.kernel.grid.model.Grid
import com.cocro.kernel.grid.model.GridMetadata
import com.cocro.kernel.grid.model.Letter
import com.cocro.kernel.grid.model.valueobject.ClueText
import com.cocro.kernel.grid.model.valueobject.GridHeight
import com.cocro.kernel.grid.model.GridTemplateSnapshot
import com.cocro.kernel.grid.model.valueobject.GridShareCode
import com.cocro.kernel.grid.model.valueobject.GridTitle
import com.cocro.kernel.grid.model.valueobject.GridWidth
import com.cocro.kernel.grid.model.valueobject.LetterValue
import com.cocro.kernel.session.enum.SessionStatus
import com.cocro.kernel.session.error.SessionError
import com.cocro.kernel.session.model.Session
import com.cocro.kernel.session.model.valueobject.SessionShareCode
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import java.util.UUID

class GetSessionGridTemplateUseCaseTest {

    private val currentUserProvider: CurrentUserProvider = mock()
    private val sessionRepository: SessionRepository = mock()
    private val gridRepository: GridRepository = mock()

    private val useCase = GetSessionGridTemplateUseCase(
        currentUserProvider,
        sessionRepository,
        gridRepository,
    )

    private val creatorId = UserId.new()
    private val participantId = UserId.new()
    private val shareCode = SessionShareCode("AB12")
    private val gridId = GridShareCode("GRID01")

    private fun minimalSnapshot() = GridTemplateSnapshot(
        shortId = gridId, title = "T", width = 5, height = 5,
        difficulty = null, author = null, reference = null,
        description = null, globalClueLabel = null,
        globalClueWordLengths = null, cells = emptyList(),
    )

    private fun buildPlayingSessionWithParticipant(): Session {
        val base = Session.create(creatorId = creatorId, shareCode = shareCode, gridId = gridId, gridTemplate = minimalSnapshot())
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

    private fun buildReferenceGrid(cells: List<Cell> = emptyList()): Grid =
        Grid(
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
            cells = cells,
        )

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
    fun `should return InvalidShareCode when shareCode is invalid`() {
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
        val outsider = AuthenticatedUser(outsiderId, setOf(Role.PLAYER))
        val session = buildPlayingSessionWithParticipant()
        whenever(currentUserProvider.currentUserOrNull()).thenReturn(outsider)
        whenever(sessionRepository.findByShareCode(shareCode)).thenReturn(session)

        // when
        val result = useCase.execute(shareCode.value)

        // then
        assertThat(result).isInstanceOf(CocroResult.Error::class.java)
        val errors = (result as CocroResult.Error).errors
        assertThat(errors).anyMatch { it is SessionError.UserNotParticipant }
        verifyNoInteractions(gridRepository)
    }

    @Test
    fun `should return ReferenceGridNotFound when grid does not exist`() {
        // given
        val user = AuthenticatedUser(participantId, setOf(Role.PLAYER))
        val session = buildPlayingSessionWithParticipant()
        whenever(currentUserProvider.currentUserOrNull()).thenReturn(user)
        whenever(sessionRepository.findByShareCode(shareCode)).thenReturn(session)
        whenever(gridRepository.findByShortId(session.gridId)).thenReturn(null)

        // when
        val result = useCase.execute(shareCode.value)

        // then
        assertThat(result).isInstanceOf(CocroResult.Error::class.java)
        val errors = (result as CocroResult.Error).errors
        assertThat(errors).anyMatch { it is SessionError.ReferenceGridNotFound }
    }

    @Test
    fun `should return GridTemplateDto with letters stripped`() {
        // given
        val user = AuthenticatedUser(participantId, setOf(Role.PLAYER))
        val session = buildPlayingSessionWithParticipant()

        val cells = listOf(
            Cell.LetterCell(
                pos = CellPos(1, 0),
                letter = Letter(
                    value = LetterValue('A'),
                    separator = SeparatorType.LEFT,
                    number = 1,
                ),
            ),
            Cell.ClueCell.SingleClueCell(
                pos = CellPos(0, 0),
                clue = Clue(
                    direction = ClueDirection.RIGHT,
                    text = ClueText("Clue text"),
                ),
            ),
            Cell.ClueCell.DoubleClueCell(
                pos = CellPos(0, 1),
                first = Clue(direction = ClueDirection.RIGHT, text = ClueText("First clue")),
                second = Clue(direction = ClueDirection.DOWN, text = ClueText("Second clue")),
            ),
            Cell.BlackCell(pos = CellPos(2, 0)),
        )
        val grid = buildReferenceGrid(cells)

        whenever(currentUserProvider.currentUserOrNull()).thenReturn(user)
        whenever(sessionRepository.findByShareCode(shareCode)).thenReturn(session)
        whenever(gridRepository.findByShortId(session.gridId)).thenReturn(grid)

        // when
        val result = useCase.execute(shareCode.value)

        // then
        assertThat(result).isInstanceOf(CocroResult.Success::class.java)
        val dto = (result as CocroResult.Success).value
        assertThat(dto.title).isEqualTo("Test Grid")
        assertThat(dto.width).isEqualTo(5)
        assertThat(dto.height).isEqualTo(5)
        assertThat(dto.cells).hasSize(4)

        val letterCell = dto.cells.first { it.type == "LETTER" }
        assertThat(letterCell.x).isEqualTo(1)
        assertThat(letterCell.y).isEqualTo(0)
        assertThat(letterCell.separator).isEqualTo("LEFT")
        assertThat(letterCell.number).isEqualTo(1)
        assertThat(letterCell.clues).isNull()

        val singleClueCell = dto.cells.first { it.type == "CLUE_SINGLE" }
        assertThat(singleClueCell.x).isEqualTo(0)
        assertThat(singleClueCell.y).isEqualTo(0)
        assertThat(singleClueCell.clues).hasSize(1)
        assertThat(singleClueCell.clues!![0].direction).isEqualTo("RIGHT")
        assertThat(singleClueCell.clues!![0].text).isEqualTo("Clue text")

        val doubleClueCell = dto.cells.first { it.type == "CLUE_DOUBLE" }
        assertThat(doubleClueCell.x).isEqualTo(0)
        assertThat(doubleClueCell.y).isEqualTo(1)
        assertThat(doubleClueCell.clues).hasSize(2)
        assertThat(doubleClueCell.clues!![0].direction).isEqualTo("RIGHT")
        assertThat(doubleClueCell.clues!![1].direction).isEqualTo("DOWN")

        val blackCell = dto.cells.first { it.type == "BLACK" }
        assertThat(blackCell.x).isEqualTo(2)
        assertThat(blackCell.y).isEqualTo(0)
        assertThat(blackCell.clues).isNull()
        assertThat(blackCell.separator).isNull()
    }
}
