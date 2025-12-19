package com.cocro.application.grid.validation.dsl.engine

import com.cocro.application.grid.dto.CellDto
import com.cocro.application.grid.dto.SubmitGridDto
import com.cocro.kernel.common.rule.SafeStringRule
import com.cocro.kernel.grid.enums.CellType
import com.cocro.kernel.grid.error.GridError
import com.cocro.kernel.grid.model.CellPos
import com.cocro.kernel.grid.rule.GridHeightRule
import com.cocro.kernel.grid.rule.GridTitleRule
import com.cocro.kernel.grid.rule.GridWidthRule
import com.cocro.kernel.grid.rule.LetterRule

/**
 * Validation engine for grid submission.
 *
 * - Contains ALL validation logic
 * - Accumulates errors (no exception throwing)
 * - Stateless from the outside, ephemeral per validation
 * - Designed to be driven by a DSL
 */
class GridValidationEngine(
    private val dto: SubmitGridDto,
) {

    /** Collected validation errors */
    val errors: MutableList<GridError> = mutableListOf()

    // ---------------------------------------------------------------------------
    // GRID-LEVEL VALIDATIONS
    // ---------------------------------------------------------------------------

    fun validateTitleRequired() {
        if (!GridTitleRule.validate(dto.title)) {
            errors += GridError.TitleMissing
        }
    }

    fun validateWidth() {
        val width = dto.width
        if (!GridWidthRule.validate(width)) {
            // TODO: introduce a dedicated InvalidGridWidth error
            errors += GridError.InvalidCellCount
        }
    }

    fun validateHeight() {
        val height = dto.height
        if (!GridHeightRule.validate(height)) {
            // TODO: introduce a dedicated InvalidGridHeight error
            errors += GridError.InvalidCellCount
        }
    }

    fun validateCellCountMatches() {
        val expected = dto.width * dto.height
        if (dto.cells.size != expected) {
            errors += GridError.InvalidCellCount
        }
    }

    // ---------------------------------------------------------------------------
    // SAFE STRING FIELDS
    // ---------------------------------------------------------------------------

    fun author(): String? = dto.author
    fun reference(): String? = dto.reference
    fun description(): String? = dto.description

    fun validateOptionalSafeString(value: String?) {
        if (value == null) return

        if (!SafeStringRule.validate(value)) {
            errors += GridError.InvalidSafeString(value)
        }
    }

    // ---------------------------------------------------------------------------
    // CELL ITERATION / CONTEXT
    // ---------------------------------------------------------------------------

    /**
     * Mutable cell context.
     *
     * - Scoped strictly to forEachCell { ... }
     * - Engine is ephemeral and mono-threaded
     * - This trade-off keeps the DSL readable
     */
    private var currentCell: CellDto? = null

    fun forEachCell(block: () -> Unit) {
        dto.cells.forEach { cell ->
            currentCell = cell
            block()
        }
        currentCell = null
    }

    fun whenCellType(
        type: CellType,
        block: () -> Unit,
    ) {
        if (currentCell?.type == type) {
            block()
        }
    }

    // ---------------------------------------------------------------------------
    // CELL VALIDATIONS
    // ---------------------------------------------------------------------------

    fun validateSingleUppercaseLetter() {
        val cell = currentCell ?: return
        val value = cell.letter

        if (value == null || value.length != 1 || !LetterRule.validate(value[0])) {
            errors += GridError.InvalidLetter(cellPos(cell))
        }
    }

    fun validateClueCount(expected: Int) {
        val cell = currentCell ?: return

        if (cell.clues == null || cell.clues.size != expected) {
            errors += GridError.InvalidClueCount(cellPos(cell))
        }
    }

    fun validateClueDirectionsDiffer() {
        val cell = currentCell ?: return
        val clues = cell.clues ?: return

        if (clues.size == 2 && clues[0].direction == clues[1].direction) {
            errors += GridError.DuplicateClueDirection(cellPos(cell))
        }
    }

    // ---------------------------------------------------------------------------
    // INTERNAL HELPERS
    // ---------------------------------------------------------------------------

    private fun cellPos(cell: CellDto): CellPos =
        CellPos(cell.x, cell.y)
}