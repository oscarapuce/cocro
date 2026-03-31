package com.cocro.application.session.mapper

import com.cocro.application.session.dto.GridTemplateClueDto
import com.cocro.application.session.dto.GridTemplateCellDto
import com.cocro.application.session.dto.GridTemplateDto
import com.cocro.infrastructure.persistence.mongo.session.document.GridTemplateCellClueDocument
import com.cocro.infrastructure.persistence.mongo.session.document.GridTemplateCellDocument
import com.cocro.infrastructure.persistence.mongo.session.document.GridTemplateDocument
import com.cocro.domain.grid.model.Cell
import com.cocro.domain.grid.model.Grid
import com.cocro.domain.grid.model.GridTemplateCellClueSnapshot
import com.cocro.domain.grid.model.GridTemplateCellSnapshot
import com.cocro.domain.grid.model.GridTemplateSnapshot
import com.cocro.domain.grid.model.valueobject.GridShareCode


internal fun Grid.toGridTemplateDto(): GridTemplateDto = GridTemplateDto(
    title = this.title.value,
    width = this.width.value,
    height = this.height.value,
    difficulty = this.metadata.difficulty,
    author = this.metadata.author.username,
    reference = this.metadata.reference,
    description = this.metadata.description,
    globalClueLabel = this.metadata.globalClue?.label,
    globalClueWordLengths = this.metadata.globalClue?.wordLengths,
    cells = this.cells.map { it.toCellDto() },
)

private fun Cell.toCellDto(): GridTemplateCellDto = when (this) {
    is Cell.LetterCell -> GridTemplateCellDto(
        x = this.pos.x, y = this.pos.y,
        type = "LETTER",
        separator = this.letter.separator.name,
        number = this.letter.number,
        clues = null,
    )
    is Cell.ClueCell.SingleClueCell -> GridTemplateCellDto(
        x = this.pos.x, y = this.pos.y,
        type = "CLUE_SINGLE",
        separator = null, number = null,
        clues = listOf(GridTemplateClueDto(direction = this.clue.direction.name, text = this.clue.text.value)),
    )
    is Cell.ClueCell.DoubleClueCell -> GridTemplateCellDto(
        x = this.pos.x, y = this.pos.y,
        type = "CLUE_DOUBLE",
        separator = null, number = null,
        clues = listOf(
            GridTemplateClueDto(direction = this.first.direction.name, text = this.first.text.value),
            GridTemplateClueDto(direction = this.second.direction.name, text = this.second.text.value),
        ),
    )
    is Cell.BlackCell -> GridTemplateCellDto(
        x = this.pos.x, y = this.pos.y,
        type = "BLACK",
        separator = null, number = null,
        clues = null,
    )
}

// Grid → GridTemplateSnapshot
internal fun Grid.toGridTemplateSnapshot(): GridTemplateSnapshot =
    GridTemplateSnapshot(
        shortId = this.shortId,
        title = this.title.value,
        width = this.width.value,
        height = this.height.value,
        difficulty = this.metadata.difficulty,
        author = this.metadata.author.username,
        reference = this.metadata.reference,
        description = this.metadata.description,
        globalClueLabel = this.metadata.globalClue?.label,
        globalClueWordLengths = this.metadata.globalClue?.wordLengths,
        cells = this.cells.map { it.toSnapshotCell() },
    )

private fun Cell.toSnapshotCell(): GridTemplateCellSnapshot = when (this) {
    is Cell.LetterCell -> GridTemplateCellSnapshot(
        x = pos.x, y = pos.y, type = "LETTER",
        separator = letter.separator.name, number = letter.number, clues = null,
    )
    is Cell.ClueCell.SingleClueCell -> GridTemplateCellSnapshot(
        x = pos.x, y = pos.y, type = "CLUE_SINGLE",
        separator = null, number = null,
        clues = listOf(GridTemplateCellClueSnapshot(direction = clue.direction.name, text = clue.text.value)),
    )
    is Cell.ClueCell.DoubleClueCell -> GridTemplateCellSnapshot(
        x = pos.x, y = pos.y, type = "CLUE_DOUBLE",
        separator = null, number = null,
        clues = listOf(
            GridTemplateCellClueSnapshot(direction = first.direction.name, text = first.text.value),
            GridTemplateCellClueSnapshot(direction = second.direction.name, text = second.text.value),
        ),
    )
    is Cell.BlackCell -> GridTemplateCellSnapshot(
        x = pos.x, y = pos.y, type = "BLACK",
        separator = null, number = null, clues = null,
    )
}

// GridTemplateSnapshot → GridTemplateDocument
internal fun GridTemplateSnapshot.toDocument(): GridTemplateDocument =
    GridTemplateDocument(
        gridShortId = shortId.value,
        title = title, width = width, height = height,
        difficulty = difficulty, author = author,
        reference = reference, description = description,
        globalClueLabel = globalClueLabel,
        globalClueWordLengths = globalClueWordLengths,
        cells = cells.map { cell ->
            GridTemplateCellDocument(
                x = cell.x, y = cell.y, type = cell.type,
                separator = cell.separator, number = cell.number,
                clues = cell.clues?.map { GridTemplateCellClueDocument(it.direction, it.text) },
            )
        },
    )

// GridTemplateDocument → GridTemplateSnapshot
internal fun GridTemplateDocument.toSnapshot(): GridTemplateSnapshot =
    GridTemplateSnapshot(
        shortId = GridShareCode(gridShortId),
        title = title, width = width, height = height,
        difficulty = difficulty, author = author,
        reference = reference, description = description,
        globalClueLabel = globalClueLabel,
        globalClueWordLengths = globalClueWordLengths,
        cells = cells.map { cell ->
            GridTemplateCellSnapshot(
                x = cell.x, y = cell.y, type = cell.type,
                separator = cell.separator, number = cell.number,
                clues = cell.clues?.map { GridTemplateCellClueSnapshot(it.direction, it.text) },
            )
        },
    )

// GridTemplateSnapshot → GridTemplateDto
internal fun GridTemplateSnapshot.toDto(): GridTemplateDto =
    GridTemplateDto(
        title = title, width = width, height = height,
        difficulty = difficulty ?: "NONE", author = author ?: "",
        reference = reference, description = description,
        globalClueLabel = globalClueLabel,
        globalClueWordLengths = globalClueWordLengths,
        cells = cells.map { cell ->
            GridTemplateCellDto(
                x = cell.x, y = cell.y, type = cell.type,
                separator = cell.separator, number = cell.number,
                clues = cell.clues?.map { GridTemplateClueDto(it.direction, it.text) },
            )
        },
    )
