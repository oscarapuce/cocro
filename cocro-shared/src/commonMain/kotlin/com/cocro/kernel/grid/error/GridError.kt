package com.cocro.kernel.grid.error

import com.cocro.kernel.common.error.CocroError
import com.cocro.kernel.common.error.ErrorCode
import com.cocro.kernel.grid.model.CellPos

sealed interface GridError : CocroError {
    object TitleMissing : GridError {
        override val errorCode = ErrorCode.GRID_TITLE_MISSING
    }

    object InvalidCellCount : GridError {
        override val errorCode = ErrorCode.GRID_INVALID_CELL_COUNT
    }

    data class GridNotFound(
        val gridId: String,
    ) : GridError {
        override val errorCode = ErrorCode.GRID_NOT_FOUND

        override fun context(): Map<String, String> =
            mapOf(
                "gridId" to gridId,
            )
    }

    data class UnauthorizedGridModification(
        val gridId: String,
    ) : GridError {
        override val errorCode = ErrorCode.GRID_UNAUTHORIZED_MODIFICATION

        override fun context(): Map<String, String> =
            mapOf(
                "gridId" to gridId,
            )
    }

    data class TitleInvalid(
        val title: String,
    ) : GridError {
        override val errorCode = ErrorCode.GRID_INVALID_TITLE

        override fun context(): Map<String, String> =
            mapOf(
                "title" to title,
            )
    }

    data class InvalidGridId(
        val gridId: String?,
    ) : GridError {
        override val errorCode = ErrorCode.GRID_INVALID_GRID_ID

        override fun context(): Map<String, String> =
            if (gridId == null) {
                mapOf(
                    "gridId" to "null",
                )
            } else {
                mapOf(
                    "gridId" to gridId,
                )
            }
    }

    data class DuplicateLetterHash(
        val otherGridId: String,
    ) : GridError {
        override val errorCode = ErrorCode.GRID_DUPLICATE_LETTER_HASH

        override fun context(): Map<String, String> =
            mapOf(
                "otherGridId" to otherGridId,
            )
    }

    data class InvalidLetter(
        val pos: CellPos,
    ) : GridError {
        override val errorCode = ErrorCode.GRID_INVALID_LETTER

        override fun context(): Map<String, String> =
            mapOf(
                "x" to pos.x.toString(),
                "y" to pos.y.toString(),
            )
    }

    data class InvalidClueCount(
        val pos: CellPos,
    ) : GridError {
        override val errorCode = ErrorCode.GRID_INVALID_CLUE_COUNT

        override fun context(): Map<String, String> =
            mapOf(
                "x" to pos.x.toString(),
                "y" to pos.y.toString(),
            )
    }

    data class DuplicateClueDirection(
        val pos: CellPos,
    ) : GridError {
        override val errorCode = ErrorCode.GRID_DUPLICATE_CLUE_DIRECTION

        override fun context(): Map<String, String> =
            mapOf(
                "x" to pos.x.toString(),
                "y" to pos.y.toString(),
            )
    }

    data class InvalidSafeString(
        val reason: String,
    ) : GridError {
        override val errorCode = ErrorCode.GRID_INVALID_SAFE_STRING

        override fun context(): Map<String, String> =
            mapOf(
                "reason" to reason,
            )
    }
}
