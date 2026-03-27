package com.cocro.domain.session.model.state

import com.cocro.domain.grid.rule.LetterRule

sealed interface SessionGridCellState {
    data class Letter(
        val value: Char,
    ) : SessionGridCellState {
        init {
            require(LetterRule.validate(value)) { "Cell letter must be capital Letter" }
        }
    }

    // Plus tard :
    // object Black : GridCellState
    // data class ClueRef(val id: ClueId) : GridCellState
}
