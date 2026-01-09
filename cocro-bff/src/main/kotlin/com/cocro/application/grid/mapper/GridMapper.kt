package com.cocro.application.grid.mapper

import com.cocro.application.grid.dto.CellDto
import com.cocro.application.grid.dto.ClueDto
import com.cocro.application.grid.dto.PatchGridDto
import com.cocro.application.grid.dto.SubmitGridDto
import com.cocro.domain.auth.valueobject.UserId
import com.cocro.domain.grid.model.Cell
import com.cocro.domain.grid.model.Clue
import com.cocro.domain.grid.model.Grid
import com.cocro.domain.grid.model.GridMetadata
import com.cocro.domain.grid.model.Letter
import com.cocro.domain.grid.valueobject.ClueText
import com.cocro.domain.grid.valueobject.GridHeight
import com.cocro.domain.grid.valueobject.GridId
import com.cocro.domain.grid.valueobject.GridTitle
import com.cocro.domain.grid.valueobject.GridWidth
import com.cocro.domain.grid.valueobject.LetterValue
import com.cocro.kernel.grid.enums.CellType
import com.cocro.kernel.grid.enums.SeparatorType
import com.cocro.kernel.grid.model.CellPos
import java.util.UUID

internal fun SubmitGridDto.toDomain(
    shortId: String,
    userId: UserId,
): Grid =
    Grid(
        id = UUID.randomUUID(),
        shortId = GridId(shortId),
        title = GridTitle(this.title),
        metadata =
            GridMetadata(
                difficulty = this.difficulty,
                author = userId,
                reference = this.reference,
                description = this.description,
            ),
        width = GridWidth(this.width),
        height = GridHeight(this.height),
        cells = this.cells.map { it.toDomain() },
    )

internal fun PatchGridDto.applyPatchTo(grid: Grid): Grid {
    // --- metadata ---
    val patchedMetadata =
        grid.metadata.copy(
            difficulty = this.difficulty ?: grid.metadata.difficulty,
            reference = this.reference ?: grid.metadata.reference,
            description = this.description ?: grid.metadata.description,
        )

    // --- dimensions ---
    val patchedWidth = this.width?.let { GridWidth(it) } ?: grid.width
    val patchedHeight = this.height?.let { GridHeight(it) } ?: grid.height

    // --- cells ---
    val patchedCells =
        this.cells
            ?.map { it.toDomain() }
            ?: grid.cells

    return grid.copy(
        title = this.title?.let { GridTitle(it) } ?: grid.title,
        metadata = patchedMetadata,
        width = patchedWidth,
        height = patchedHeight,
        cells = patchedCells,
    )
}

private fun CellDto.toDomain(): Cell {
    val pos = CellPos(this.x, this.y)

    return when (this.type) {
        CellType.LETTER ->
            Cell.LetterCell(
                pos,
                Letter(
                    value = LetterValue(this.letter!![0]),
                    separator = this.separator ?: SeparatorType.NONE,
                    number = this.number,
                ),
            )

        CellType.CLUE_SINGLE ->
            Cell.ClueCell.SingleClueCell(pos, this.clues!![0].toDomain())

        CellType.CLUE_DOUBLE ->
            Cell.ClueCell.DoubleClueCell(
                pos,
                this.clues!![0].toDomain(),
                this.clues[1].toDomain(),
            )

        CellType.BLACK ->
            Cell.BlackCell(pos)
    }
}

private fun ClueDto.toDomain(): Clue =
    Clue(
        direction = this.direction,
        text = ClueText(this.text),
    )
