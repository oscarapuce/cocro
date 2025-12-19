package com.cocro.application.grid.validation.dsl.engine

import com.cocro.application.grid.dto.SubmitGridDto
import com.cocro.kernel.grid.error.GridError

internal class GridValidationDsl(
    private val engine: GridValidationEngine,
) {
    fun title(block: TitleDsl.() -> Unit) =
        TitleDsl(engine).block()

    fun size(block: SizeDsl.() -> Unit) =
        SizeDsl(engine).block()

    fun cells(block: CellsDsl.() -> Unit) =
        CellsDsl(engine).block()

    fun author(block: SafeStringDsl.() -> Unit) =
        SafeStringDsl(engine) { engine.author() }.block()

    fun reference(block: SafeStringDsl.() -> Unit) =
        SafeStringDsl(engine) { engine.reference() }.block()

    fun description(block: SafeStringDsl.() -> Unit) =
        SafeStringDsl(engine) { engine.description() }.block()
}

internal fun validateGrid(
    dto: SubmitGridDto,
    block: GridValidationDsl.() -> Unit,
): List<GridError> {
    val engine = GridValidationEngine(dto)
    GridValidationDsl(engine).block()
    return engine.errors
}
