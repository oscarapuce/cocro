package com.cocro.kernel.grid.model

import com.cocro.kernel.grid.rule.DoubleClueDirectionRule

sealed interface Cell {
    val pos: CellPos

    data class LetterCell(
        override val pos: CellPos,
        val letter: Letter
    ) : Cell

    sealed interface ClueCell : Cell {
        data class SingleClueCell(
            override val pos: CellPos,
            val clue: Clue
        ) : Cell

        data class DoubleClueCell(
            override val pos: CellPos,
            val first: Clue,
            val second: Clue
        ) : Cell {
            init {
                require(DoubleClueDirectionRule.validate(this)) {
                    "Clue directions must be different"
                }
            }
        }

    }

    data class BlackCell(
        override val pos: CellPos
    ) : Cell

}
