package com.cocro.application.grid.validation.dsl.engine

import com.cocro.application.common.validation.Presence
import com.cocro.application.common.validation.RuleDsl
import com.cocro.application.grid.dto.CellDto
import com.cocro.application.grid.dto.GridDto
import com.cocro.kernel.common.rule.SafeStringRule
import com.cocro.kernel.grid.enums.CellType
import com.cocro.kernel.grid.error.GridError
import com.cocro.kernel.grid.model.CellPos
import com.cocro.kernel.grid.rule.GridHeightRule
import com.cocro.kernel.grid.rule.GridShareCodeRule
import com.cocro.kernel.grid.rule.GridTitleRule
import com.cocro.kernel.grid.rule.GridWidthRule
import com.cocro.kernel.grid.rule.LetterRule

class GridValidationEngine(
    private val dto: GridDto,
) {
    val errors = mutableListOf<GridError>()

    // -------------------------------------------------------------------------
    // GRID ID
    // -------------------------------------------------------------------------

    fun validateGridId(presence: Presence) =
        RuleDsl(presence, { dto.gridId }) {
            if (!GridShareCodeRule.validate(dto.gridId!!)) {
                errors += GridError.InvalidGridId(dto.gridId!!)
            }
        }.run()

    // -------------------------------------------------------------------------
    // TITLE
    // -------------------------------------------------------------------------

    fun validateTitle(presence: Presence) =
        RuleDsl(presence, { dto.title }) {
            if (!GridTitleRule.validate(dto.title!!)) {
                errors += GridError.TitleInvalid(dto.title!!)
            }
        }.run()

    // -------------------------------------------------------------------------
    // SAFE STRING
    // -------------------------------------------------------------------------

    fun validateSafeString(
        presence: Presence,
        value: () -> String?,
    ) = RuleDsl(presence, value) {
        if (!SafeStringRule.validate(value()!!)) {
            errors += GridError.InvalidSafeString(value()!!)
        }
    }.run()

    // -------------------------------------------------------------------------
    // SIZE (cohérence partielle)
    // -------------------------------------------------------------------------

    fun validateSize(presence: Presence) =
        RuleDsl(presence, {
            dto.width != null || dto.height != null || dto.cells != null
        }) {
            val hasWidth = dto.width != null
            val hasHeight = dto.height != null
            val hasCells = dto.cells != null

            // ---- invariant PATCH ----
            // Either all (width, height, cells) are present, or none
            if (!(hasWidth && hasHeight && hasCells)) {
                errors += GridError.InvalidCellCount
                return@RuleDsl
            }

            val w = dto.width!!
            val h = dto.height!!
            val cells = dto.cells!!

            // ---- dimension rules ----
            if (!GridWidthRule.validate(w) || !GridHeightRule.validate(h)) {
                errors += GridError.InvalidCellCount
                return@RuleDsl
            }

            // ---- consistency rule ----
            if (cells.size != w * h) {
                errors += GridError.InvalidCellCount
            }
        }.run()

    // -------------------------------------------------------------------------
    // CELLS
    // -------------------------------------------------------------------------

    private var currentCell: CellDto? = null

    fun forEachCell(block: () -> Unit) {
        dto.cells?.forEach {
            currentCell = it
            block()
        }
        currentCell = null
    }

    fun whenCellType(
        type: CellType,
        block: () -> Unit,
    ) {
        if (currentCell?.type == type) block()
    }

    fun validateSingleUppercaseLetter() {
        val cell = currentCell ?: return
        val v = cell.letter
        if (v == null || v.length != 1 || !LetterRule.validate(v[0])) {
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

    private fun cellPos(cell: CellDto): CellPos = CellPos(cell.x, cell.y)

    // -------------------------------------------------------------------------
    // DTO READ ACCESS (for DSL only)
    // -------------------------------------------------------------------------

    internal fun getReference(): String? = dto.reference

    internal fun getDescription(): String? = dto.description
}
