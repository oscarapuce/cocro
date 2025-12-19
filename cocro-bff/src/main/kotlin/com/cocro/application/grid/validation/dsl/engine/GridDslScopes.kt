package com.cocro.application.grid.validation.dsl.engine

import com.cocro.kernel.grid.enums.CellType

class TitleDsl(private val engine: GridValidationEngine) {
    fun required() = engine.validateTitleRequired()
}

class SizeDsl(private val engine: GridValidationEngine) {
    fun width(block: RuleDsl.() -> Unit) =
        RuleDsl { engine.validateWidth() }.block()

    fun height(block: RuleDsl.() -> Unit) =
        RuleDsl { engine.validateHeight() }.block()

    fun cellCountMatches() =
        engine.validateCellCountMatches()
}

class CellsDsl(private val engine: GridValidationEngine) {
    fun each(block: CellDsl.() -> Unit) {
        engine.forEachCell {
            CellDsl(engine).block()
        }
    }
}

class CellDsl(private val engine: GridValidationEngine) {
    fun whenType(
        type: CellType,
        block: CellTypeDsl.() -> Unit,
    ) {
        engine.whenCellType(type) {
            CellTypeDsl(engine).block()
        }
    }
}

class CellTypeDsl(private val engine: GridValidationEngine) {
    fun letter(block: LetterDsl.() -> Unit) =
        LetterDsl(engine).block()

    fun clues(block: CluesDsl.() -> Unit) =
        CluesDsl(engine).block()
}

class LetterDsl(private val engine: GridValidationEngine) {
    fun singleUppercase() =
        engine.validateSingleUppercaseLetter()
}

class CluesDsl(private val engine: GridValidationEngine) {
    fun exactly(count: Int) =
        engine.validateClueCount(count)

    fun directionsMustDiffer() =
        engine.validateClueDirectionsDiffer()
}

class RuleDsl(
    private val action: () -> Unit,
) {
    fun apply() = action()
}

class SafeStringDsl(
    private val engine: GridValidationEngine,
    private val select: () -> String?,
) {
    fun optionalSafeString() {
        engine.validateOptionalSafeString(select())
    }
}
