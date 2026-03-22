package com.cocro.kernel.session.model.state

import com.cocro.kernel.auth.model.valueobject.UserId
import com.cocro.kernel.grid.model.Cell
import com.cocro.kernel.grid.model.CellPos
import com.cocro.kernel.grid.model.Grid
import com.cocro.kernel.grid.model.GridMetadata
import com.cocro.kernel.grid.model.Letter
import com.cocro.kernel.grid.model.valueobject.GridHeight
import com.cocro.kernel.grid.model.valueobject.GridShareCode
import com.cocro.kernel.grid.model.valueobject.GridTitle
import com.cocro.kernel.grid.model.valueobject.GridWidth
import com.cocro.kernel.grid.model.valueobject.LetterValue
import com.cocro.kernel.session.model.valueobject.SessionId
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
        title = GridTitle("Test Grid"),
        metadata = GridMetadata(
            author = UserId.new(),
            reference = null,
            description = null,
            difficulty = "NONE",
        ),
        width = GridWidth(5),
        height = GridHeight(5),
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
    }
}
