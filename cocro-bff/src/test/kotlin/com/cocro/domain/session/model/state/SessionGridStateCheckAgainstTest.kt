package com.cocro.domain.session.model.state

import com.cocro.domain.auth.model.valueobject.UserId
import com.cocro.domain.grid.model.Cell
import com.cocro.domain.grid.model.CellPos
import com.cocro.domain.grid.model.Grid
import com.cocro.domain.common.model.Author
import com.cocro.domain.grid.model.GridMetadata
import com.cocro.domain.grid.model.Letter
import com.cocro.domain.grid.model.valueobject.GridDimension
import com.cocro.domain.grid.model.valueobject.GridHeight
import com.cocro.domain.grid.model.valueobject.GridShareCode
import com.cocro.domain.grid.model.valueobject.GridTitle
import com.cocro.domain.grid.model.valueobject.GridWidth
import com.cocro.domain.grid.model.valueobject.LetterValue
import com.cocro.domain.session.model.valueobject.SessionId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

class SessionGridStateCheckAgainstTest {

    private val sessionId = SessionId.new()
    private val gridShareCode = GridShareCode("ABC123")
    private val actorId = UserId.new()

    private fun letterCell(x: Int, y: Int, letter: Char) =
        Cell.LetterCell(CellPos(x, y), Letter(LetterValue(letter)))

    private fun buildGrid(cells: List<Cell>): Grid = Grid(
        id = UUID.randomUUID(),
        shortId = gridShareCode,
        metadata = GridMetadata(
            title = GridTitle("Test Grid"), author = Author(id = UserId.new(), username = "Test"),
            reference = null,
            description = null,
            difficulty = "NONE",
        ),
        dimension = GridDimension(width = GridWidth(5), height = GridHeight(5)),
        cells = cells,
    )

    private fun stateWithLetters(vararg entries: Triple<Int, Int, Char>): SessionGridState {
        var state = SessionGridState.initial(sessionId, gridShareCode)
        for ((x, y, letter) in entries) {
            state = state.apply(
                SessionGridCommand.SetLetter(sessionId, actorId, CellPos(x, y), letter)
            )
        }
        return state
    }

    @Test
    fun `empty state against grid with letters — not complete, 0 filled`() {
        val grid = buildGrid(listOf(letterCell(0, 0, 'A'), letterCell(1, 0, 'B')))
        val state = SessionGridState.initial(sessionId, gridShareCode)

        val result = state.checkAgainst(grid)

        assertThat(result.isComplete).isFalse()
        assertThat(result.isCorrect).isFalse()
        assertThat(result.filledCount).isEqualTo(0)
        assertThat(result.totalCount).isEqualTo(2)
        assertThat(result.wrongCount).isEqualTo(0)
        assertThat(result.correctCount).isEqualTo(0)
    }

    @Test
    fun `partially filled state — not complete`() {
        val grid = buildGrid(listOf(letterCell(0, 0, 'A'), letterCell(1, 0, 'B')))
        val state = stateWithLetters(Triple(0, 0, 'A'))

        val result = state.checkAgainst(grid)

        assertThat(result.isComplete).isFalse()
        assertThat(result.isCorrect).isFalse()
        assertThat(result.filledCount).isEqualTo(1)
        assertThat(result.totalCount).isEqualTo(2)
        assertThat(result.wrongCount).isEqualTo(0)
        assertThat(result.correctCount).isEqualTo(1)
    }

    @Test
    fun `fully filled with correct letters — complete and correct`() {
        val grid = buildGrid(listOf(letterCell(0, 0, 'A'), letterCell(1, 0, 'B')))
        val state = stateWithLetters(Triple(0, 0, 'A'), Triple(1, 0, 'B'))

        val result = state.checkAgainst(grid)

        assertThat(result.isComplete).isTrue()
        assertThat(result.isCorrect).isTrue()
        assertThat(result.filledCount).isEqualTo(2)
        assertThat(result.totalCount).isEqualTo(2)
        assertThat(result.wrongCount).isEqualTo(0)
        assertThat(result.correctCount).isEqualTo(2)
    }

    @Test
    fun `fully filled with wrong letters — complete but not correct`() {
        val grid = buildGrid(listOf(letterCell(0, 0, 'A'), letterCell(1, 0, 'B')))
        val state = stateWithLetters(Triple(0, 0, 'X'), Triple(1, 0, 'Y'))

        val result = state.checkAgainst(grid)

        assertThat(result.isComplete).isTrue()
        assertThat(result.isCorrect).isFalse()
        assertThat(result.filledCount).isEqualTo(2)
        assertThat(result.totalCount).isEqualTo(2)
        assertThat(result.wrongCount).isEqualTo(2)
        assertThat(result.correctCount).isEqualTo(0)
    }

    @Test
    fun `grid with only black cells — 0 total, complete and correct`() {
        val grid = buildGrid(listOf(Cell.BlackCell(CellPos(0, 0))))
        val state = SessionGridState.initial(sessionId, gridShareCode)

        val result = state.checkAgainst(grid)

        assertThat(result.isComplete).isTrue()
        assertThat(result.isCorrect).isTrue()
        assertThat(result.filledCount).isEqualTo(0)
        assertThat(result.totalCount).isEqualTo(0)
        assertThat(result.wrongCount).isEqualTo(0)
        assertThat(result.correctCount).isEqualTo(0)
    }

    @Test
    fun `extra cells in state that are not in reference grid do not affect result`() {
        val grid = buildGrid(listOf(letterCell(0, 0, 'A')))
        val state = stateWithLetters(Triple(0, 0, 'A'), Triple(9, 9, 'Z'))

        val result = state.checkAgainst(grid)

        assertThat(result.isComplete).isTrue()
        assertThat(result.isCorrect).isTrue()
        assertThat(result.filledCount).isEqualTo(1)
        assertThat(result.totalCount).isEqualTo(1)
        assertThat(result.wrongCount).isEqualTo(0)
        assertThat(result.correctCount).isEqualTo(1)
    }

    @Test
    fun `partially filled with one wrong letter — not complete, 1 wrong`() {
        val grid = buildGrid(listOf(letterCell(0, 0, 'A'), letterCell(1, 0, 'B'), letterCell(2, 0, 'C')))
        val state = stateWithLetters(Triple(0, 0, 'A'), Triple(1, 0, 'Z'))

        val result = state.checkAgainst(grid)

        assertThat(result.isComplete).isFalse()
        assertThat(result.isCorrect).isFalse()
        assertThat(result.filledCount).isEqualTo(2)
        assertThat(result.totalCount).isEqualTo(3)
        assertThat(result.wrongCount).isEqualTo(1)
        assertThat(result.correctCount).isEqualTo(1)
    }
}
