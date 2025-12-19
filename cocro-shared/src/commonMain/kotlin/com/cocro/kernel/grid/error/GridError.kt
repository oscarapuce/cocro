package com.cocro.kernel.grid.error

import com.cocro.kernel.common.error.CocroError
import com.cocro.kernel.common.error.ErrorCode
import com.cocro.kernel.grid.model.CellPos

sealed interface GridError : CocroError {

    object TitleMissing : GridError {
        override val errorCode = ErrorCode.GRID_TITLE_MISSING
    }

    object DifficultyMissing : GridError {
        override val errorCode = ErrorCode.GRID_DIFFICULTY_MISSING
    }

    object InvalidCellCount : GridError {
        override val errorCode = ErrorCode.GRID_INVALID_CELL_COUNT
    }

    object DuplicateLetterHash : GridError {
        override val errorCode = ErrorCode.GRID_DUPLICATE_LETTER_HASH
    }

    data class InvalidLetter(val pos: CellPos) : GridError {
        override val errorCode = ErrorCode.GRID_INVALID_LETTER
    }

    data class InvalidClueCount(val pos: CellPos) : GridError {
        override val errorCode = ErrorCode.GRID_INVALID_CLUE_COUNT
    }

    data class DuplicateClueDirection(val pos: CellPos) : GridError {
        override val errorCode = ErrorCode.GRID_DUPLICATE_CLUE_DIRECTION
    }
}
