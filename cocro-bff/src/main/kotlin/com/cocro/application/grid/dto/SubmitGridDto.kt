package com.cocro.application.grid.dto

import com.cocro.kernel.grid.enums.CellType
import com.cocro.kernel.grid.enums.ClueDirection
import com.cocro.kernel.grid.enums.GridDifficulty
import com.cocro.kernel.grid.enums.SeparatorType

data class SubmitGridDto(
    val title: String,
    val difficulty: GridDifficulty,
    val author: String?,
    val reference: String?,
    val description: String?,
    val width: Int,
    val height: Int,
    val cells: List<CellDto>,
)

data class ClueDto(
    val direction: ClueDirection,
    val text: String,
)

data class CellDto(
    val x: Int,
    val y: Int,
    val type: CellType,
    val letter: String?,
    val separator: SeparatorType?,
    val number: Int?,
    val clues: List<ClueDto>?,
)