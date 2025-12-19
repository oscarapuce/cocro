package com.cocro.application.grid.mapper

import com.cocro.application.grid.dto.CellDto
import com.cocro.application.grid.dto.ClueDto
import com.cocro.application.grid.dto.SubmitGridDto
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

fun SubmitGridDto.toDomain(shortId: String): Grid =
    Grid(
        id = UUID.randomUUID(),
        shortId = GridId(shortId),
        title = GridTitle(this.title),
        metadata =
            GridMetadata(
                difficulty = this.difficulty,
                author = this.author,
                reference = this.reference,
                description = this.description,
            ),
        width = GridWidth(this.width),
        height = GridHeight(this.height),
        cells = this.cells.map { it.toDomain() },
    )

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
