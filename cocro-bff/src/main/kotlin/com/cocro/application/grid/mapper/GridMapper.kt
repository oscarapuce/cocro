package com.cocro.application.grid.mapper

import com.cocro.application.grid.dto.CellDto
import com.cocro.application.grid.dto.ClueDto
import com.cocro.application.grid.dto.GlobalClueDto
import com.cocro.application.grid.dto.GridFullCellDto
import com.cocro.application.grid.dto.GridFullDto
import com.cocro.application.grid.dto.GridFullLetterDto
import com.cocro.application.grid.dto.GridSummaryDto
import com.cocro.application.grid.dto.PatchGridDto
import com.cocro.application.grid.dto.SubmitGridDto
import com.cocro.domain.auth.model.valueobject.UserId
import com.cocro.domain.common.model.Author
import com.cocro.domain.grid.enums.CellType
import com.cocro.domain.grid.enums.SeparatorType
import com.cocro.domain.grid.model.Cell
import com.cocro.domain.grid.model.CellPos
import com.cocro.domain.grid.model.Clue
import com.cocro.domain.grid.model.GlobalClue
import com.cocro.domain.grid.model.Grid
import com.cocro.domain.grid.model.GridMetadata
import com.cocro.domain.grid.model.Letter
import com.cocro.domain.grid.model.valueobject.ClueText
import com.cocro.domain.grid.model.valueobject.GridDimension
import com.cocro.domain.grid.model.valueobject.GridHeight
import com.cocro.domain.grid.model.valueobject.GridShareCode
import com.cocro.domain.grid.model.valueobject.GridTitle
import com.cocro.domain.grid.model.valueobject.GridWidth
import com.cocro.domain.grid.model.valueobject.LetterValue
import java.util.UUID

private val VALID_DIFFICULTIES = setOf("NONE", "0", "1", "2", "3", "4", "5", "0-1", "1-2", "2-3", "3-4", "4-5")

private fun normalizeDifficulty(raw: String?): String =
    if (raw != null && raw in VALID_DIFFICULTIES) raw else "NONE"

internal fun SubmitGridDto.toDomain(
    shortId: GridShareCode,
    author: Author,
): Grid =
    Grid(
        id = UUID.randomUUID(),
        shortId = shortId,
        metadata = GridMetadata(
            title = GridTitle(this.title),
            difficulty = normalizeDifficulty(this.difficulty),
            author = author,
            reference = this.reference,
            description = this.description,
            globalClue = if (this.globalClueLabel != null)
                GlobalClue(this.globalClueLabel, this.globalClueWordLengths ?: emptyList())
            else null,
        ),
        dimension = GridDimension(
            width = GridWidth(this.width),
            height = GridHeight(this.height),
        ),
        cells = this.cells.map { it.toDomain() },
    )

internal fun PatchGridDto.applyPatchTo(grid: Grid): Grid {
    val patchedGlobalClue = if (this.globalClueLabel != null)
        GlobalClue(this.globalClueLabel, this.globalClueWordLengths ?: emptyList())
    else grid.metadata.globalClue

    val patchedMetadata = grid.metadata.copy(
        title = this.title?.let { GridTitle(it) } ?: grid.metadata.title,
        difficulty = this.difficulty?.let { normalizeDifficulty(it) } ?: grid.metadata.difficulty,
        reference = this.reference ?: grid.metadata.reference,
        description = this.description ?: grid.metadata.description,
        globalClue = patchedGlobalClue,
    )
    val patchedDimension = GridDimension(
        width = this.width?.let { GridWidth(it) } ?: grid.width,
        height = this.height?.let { GridHeight(it) } ?: grid.height,
    )
    val patchedCells = this.cells?.map { it.toDomain() } ?: grid.cells

    return grid.copy(
        metadata = patchedMetadata,
        dimension = patchedDimension,
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

internal fun Grid.toSummaryDto(): GridSummaryDto =
    GridSummaryDto(
        gridId = shortId.value,
        title = title.value,
        width = width.value,
        height = height.value,
        difficulty = metadata.difficulty,
        createdAt = createdAt.toString(),
        updatedAt = updatedAt.toString(),
    )

internal fun Grid.toFullDto(): GridFullDto =
    GridFullDto(
        gridId = shortId.value,
        title = title.value,
        width = width.value,
        height = height.value,
        difficulty = metadata.difficulty,
        description = metadata.description,
        reference = metadata.reference,
        author = metadata.author.username,
        cells = cells.map { it.toFullCellDto() },
        globalClue = metadata.globalClue?.let { GlobalClueDto(it.label, it.wordLengths) },
    )

private fun Cell.toFullCellDto(): GridFullCellDto =
    when (this) {
        is Cell.LetterCell -> GridFullCellDto(
            x = pos.x,
            y = pos.y,
            type = CellType.LETTER,
            letter = GridFullLetterDto(
                value = letter.value.value.toString(),
                separator = letter.separator,
                number = letter.number,
            ),
            clues = null,
        )
        is Cell.ClueCell.SingleClueCell -> GridFullCellDto(
            x = pos.x,
            y = pos.y,
            type = CellType.CLUE_SINGLE,
            letter = null,
            clues = listOf(ClueDto(direction = clue.direction, text = clue.text.value)),
        )
        is Cell.ClueCell.DoubleClueCell -> GridFullCellDto(
            x = pos.x,
            y = pos.y,
            type = CellType.CLUE_DOUBLE,
            letter = null,
            clues = listOf(
                ClueDto(direction = first.direction, text = first.text.value),
                ClueDto(direction = second.direction, text = second.text.value),
            ),
        )
        is Cell.BlackCell -> GridFullCellDto(
            x = pos.x,
            y = pos.y,
            type = CellType.BLACK,
            letter = null,
            clues = null,
        )
    }
