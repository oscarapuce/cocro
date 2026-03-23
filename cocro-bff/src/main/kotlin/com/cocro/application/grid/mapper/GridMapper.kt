package com.cocro.application.grid.mapper

import com.cocro.application.grid.dto.CellDto
import com.cocro.application.grid.dto.ClueDto
import com.cocro.application.grid.dto.PatchGridDto
import com.cocro.application.grid.dto.SubmitGridDto
import com.cocro.kernel.auth.model.valueobject.UserId
import com.cocro.kernel.grid.enums.CellType
import com.cocro.kernel.grid.enums.SeparatorType
import com.cocro.kernel.grid.model.Cell
import com.cocro.kernel.grid.model.CellPos
import com.cocro.kernel.grid.model.Clue
import com.cocro.kernel.grid.model.Grid
import com.cocro.kernel.grid.model.GridMetadata
import com.cocro.kernel.grid.model.Letter
import com.cocro.kernel.grid.model.valueobject.ClueText
import com.cocro.kernel.grid.model.valueobject.GridHeight
import com.cocro.kernel.grid.model.valueobject.GridShareCode
import com.cocro.kernel.grid.model.valueobject.GridTitle
import com.cocro.kernel.grid.model.valueobject.GridWidth
import com.cocro.kernel.grid.model.valueobject.LetterValue
import java.util.UUID

private val VALID_DIFFICULTIES = setOf("NONE", "0", "1", "2", "3", "4", "5", "0-1", "1-2", "2-3", "3-4", "4-5")

private fun normalizeDifficulty(raw: String?): String =
    if (raw != null && raw in VALID_DIFFICULTIES) raw else "NONE"

internal fun SubmitGridDto.toDomain(
    shortId: GridShareCode,
    userId: UserId,
): Grid =
    Grid(
        id = UUID.randomUUID(),
        shortId = shortId,
        title = GridTitle(this.title),
        metadata = GridMetadata(
            difficulty = normalizeDifficulty(this.difficulty),
            author = userId,
            reference = this.reference,
            description = this.description,
            globalClueLabel = this.globalClueLabel,
            globalClueWordLengths = this.globalClueWordLengths,
        ),
        width = GridWidth(this.width),
        height = GridHeight(this.height),
        cells = this.cells.map { it.toDomain() },
    )

internal fun PatchGridDto.applyPatchTo(grid: Grid): Grid {
    val patchedMetadata = grid.metadata.copy(
        difficulty = this.difficulty?.let { normalizeDifficulty(it) } ?: grid.metadata.difficulty,
        reference = this.reference ?: grid.metadata.reference,
        description = this.description ?: grid.metadata.description,
        globalClueLabel = this.globalClueLabel ?: grid.metadata.globalClueLabel,
        globalClueWordLengths = this.globalClueWordLengths ?: grid.metadata.globalClueWordLengths,
    )
    val patchedWidth = this.width?.let { GridWidth(it) } ?: grid.width
    val patchedHeight = this.height?.let { GridHeight(it) } ?: grid.height
    val patchedCells = this.cells?.map { it.toDomain() } ?: grid.cells

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
        CellType.BLACK -> Cell.BlackCell(pos)
    }
}

private fun ClueDto.toDomain(): Clue =
    Clue(direction = this.direction, text = ClueText(this.text))
