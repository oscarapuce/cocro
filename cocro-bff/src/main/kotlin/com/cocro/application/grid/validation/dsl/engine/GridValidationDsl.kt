package com.cocro.application.grid.validation.dsl.engine

import com.cocro.application.grid.dto.GridDto
import com.cocro.kernel.grid.error.GridError

internal class GridValidationDsl(
    private val engine: GridValidationEngine,
) {
    fun gridId(block: GridIdDsl.() -> Unit) = GridIdDsl(engine).block()

    fun title(block: TitleDsl.() -> Unit) = TitleDsl(engine).block()

    fun size(block: SizeDsl.() -> Unit) = SizeDsl(engine).block()

    fun cells(block: CellsDsl.() -> Unit) = CellsDsl(engine).block()

    fun reference(block: SafeStringDsl.() -> Unit) = SafeStringDsl(engine) { engine.getReference() }.block()

    fun description(block: SafeStringDsl.() -> Unit) = SafeStringDsl(engine) { engine.getDescription() }.block()
}

internal fun validateGrid(
    dto: GridDto,
    block: GridValidationDsl.() -> Unit,
): List<GridError> {
    val engine = GridValidationEngine(dto)
    GridValidationDsl(engine).block()
    return engine.errors
}
