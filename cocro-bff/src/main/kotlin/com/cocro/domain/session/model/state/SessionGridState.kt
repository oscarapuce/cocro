package com.cocro.domain.session.model.state

import com.cocro.domain.grid.model.Cell
import com.cocro.domain.grid.model.CellPos
import com.cocro.domain.grid.model.Grid
import com.cocro.domain.grid.model.valueobject.GridShareCode
import com.cocro.domain.grid.rule.LetterRule
import com.cocro.domain.session.model.valueobject.SessionId
import com.cocro.domain.session.model.valueobject.SessionShareCode

data class SessionGridState(
    val sessionId: SessionId,
    val gridShareCode: GridShareCode,
    val revision: SessionGridStateRevision,
    val cells: Map<CellPos, SessionGridCellState>,
) {
    fun apply(command: SessionGridCommand): SessionGridState =
        when (command) {
            is SessionGridCommand.SetLetter -> applySetLetter(command)
            is SessionGridCommand.ClearCell -> applyClearCell(command)
        }

    private fun applySetLetter(cmd: SessionGridCommand.SetLetter): SessionGridState {
        require(LetterRule.validate(cmd.letter)) { "Invalid letter" }

        return copy(
            cells = cells + (cmd.position to SessionGridCellState.Letter(cmd.letter)),
            revision = revision.next(),
        )
    }

    private fun applyClearCell(cmd: SessionGridCommand.ClearCell): SessionGridState =
        copy(
            cells = cells - cmd.position,
            revision = revision.next(),
        )

    /**
     * Checks this session grid state against the reference [grid].
     *
     * Iterates every [Cell.LetterCell] in the reference grid and compares it with
     * the corresponding entry in [cells]. The comparison is purely positional and
     * case-sensitive (both sides store uppercase chars via [LetterRule]).
     *
     * This is pure domain logic — no side effects, no state mutation.
     */
    fun checkAgainst(grid: Grid): GridCheckResult {
        val referenceLetters: Map<CellPos, Char> = grid.cells
            .filterIsInstance<Cell.LetterCell>()
            .associate { it.pos to it.letter.value.value }

        val totalCount = referenceLetters.size
        val filledCount = referenceLetters.keys.count { pos -> cells.containsKey(pos) }
        val wrongCount = referenceLetters.count { (pos, expectedChar) ->
            val placed = (cells[pos] as? SessionGridCellState.Letter)?.value
            placed != null && placed != expectedChar
        }
        val isComplete = filledCount == totalCount
        val isCorrect = isComplete && wrongCount == 0

        return GridCheckResult(
            isComplete = isComplete,
            isCorrect = isCorrect,
            filledCount = filledCount,
            totalCount = totalCount,
            wrongCount = wrongCount,
            correctCount = filledCount - wrongCount,
        )
    }

    companion object {
        fun initial(
            sessionId: SessionId,
            gridShareCode: GridShareCode,
        ): SessionGridState =
            SessionGridState(
                sessionId = sessionId,
                gridShareCode = gridShareCode,
                revision = SessionGridStateRevision.initial(),
                cells = emptyMap(),
            )
    }
}
