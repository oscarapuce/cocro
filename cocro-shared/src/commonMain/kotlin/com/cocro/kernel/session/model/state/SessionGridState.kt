package com.cocro.kernel.session.model.state

import com.cocro.kernel.grid.model.CellPos
import com.cocro.kernel.grid.model.valueobject.GridShareCode
import com.cocro.kernel.grid.rule.LetterRule
import com.cocro.kernel.session.model.valueobject.SessionId
import com.cocro.kernel.session.model.valueobject.SessionShareCode

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
