package com.cocro.application.session.mapper

import com.cocro.application.session.dto.GridTemplateClueDto
import com.cocro.application.session.dto.GridTemplateCellDto
import com.cocro.application.session.dto.GridTemplateDto
import com.cocro.kernel.grid.model.Cell
import com.cocro.kernel.grid.model.Grid

internal fun Grid.toGridTemplateDto(): GridTemplateDto = GridTemplateDto(
    title = this.title.value,
    width = this.width.value,
    height = this.height.value,
    difficulty = this.metadata.difficulty,
    author = this.metadata.author.toString(),
    reference = this.metadata.reference,
    description = this.metadata.description,
    globalClueLabel = this.metadata.globalClueLabel,
    globalClueWordLengths = this.metadata.globalClueWordLengths,
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
