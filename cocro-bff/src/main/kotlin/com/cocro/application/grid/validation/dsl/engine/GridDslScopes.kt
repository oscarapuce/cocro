package com.cocro.application.grid.validation.dsl.engine

import com.cocro.application.common.validation.Presence
import com.cocro.kernel.grid.enums.CellType

class GridIdDsl(
    private val engine: GridValidationEngine,
) {
    fun required() = engine.validateGridId(Presence.REQUIRED)
}

class TitleDsl(
    private val engine: GridValidationEngine,
) {
    fun required() = engine.validateTitle(Presence.REQUIRED)

    fun optional() = engine.validateTitle(Presence.OPTIONAL)
}

class CellsDsl(
    private val engine: GridValidationEngine,
) {
    fun each(block: CellDsl.() -> Unit) {
        engine.forEachCell {
            CellDsl(engine).block()
        }
    }
}

class CellDsl(
    private val engine: GridValidationEngine,
) {
    fun whenType(
        type: CellType,
        block: CellTypeDsl.() -> Unit,
    ) {
        engine.whenCellType(type) {
            CellTypeDsl(engine).block()
        }
    }
}

class CellTypeDsl(
    private val engine: GridValidationEngine,
) {
    fun letter(block: LetterDsl.() -> Unit) = LetterDsl(engine).block()

    fun clues(block: CluesDsl.() -> Unit) = CluesDsl(engine).block()
}

class LetterDsl(
    private val engine: GridValidationEngine,
) {
    fun singleUppercase() = engine.validateSingleUppercaseLetter()
}

class CluesDsl(
    private val engine: GridValidationEngine,
) {
    fun exactly(count: Int) = engine.validateClueCount(count)

    fun directionsMustDiffer() = engine.validateClueDirectionsDiffer()
}

class SafeStringDsl(
    private val engine: GridValidationEngine,
    private val value: () -> String?,
) {
    fun optional() = engine.validateSafeString(Presence.OPTIONAL, value)
}

class SizeDsl(
    private val engine: GridValidationEngine,
) {
    fun required() = engine.validateSize(Presence.REQUIRED)

    fun optional() = engine.validateSize(Presence.OPTIONAL)
}
