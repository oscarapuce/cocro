package com.cocro.application.grid.dto

import com.cocro.kernel.grid.enums.CellType
import com.cocro.kernel.grid.enums.ClueDirection
import com.cocro.kernel.grid.enums.GridDifficulty
import com.cocro.kernel.grid.enums.SeparatorType

interface GridDto {
    val gridId: String?
    val title: String?
    val difficulty: GridDifficulty?
    val reference: String?
    val description: String?
    val width: Int?
    val height: Int?
    val cells: List<CellDto>?
}

data class SubmitGridDto(
    override val gridId: String? = null,
    override val title: String,
    override val difficulty: GridDifficulty,
    override val reference: String?,
    override val description: String?,
    override val width: Int,
    override val height: Int,
    override val cells: List<CellDto>,
) : GridDto

data class PatchGridDto(
    override val gridId: String,
    override val title: String?,
    override val difficulty: GridDifficulty?,
    override val reference: String?,
    override val description: String?,
    override val width: Int?,
    override val height: Int?,
    override val cells: List<CellDto>?,
) : GridDto

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
