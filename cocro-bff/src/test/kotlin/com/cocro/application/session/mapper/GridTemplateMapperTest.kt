package com.cocro.application.session.mapper

import com.cocro.domain.grid.enums.ClueDirection
import com.cocro.domain.grid.enums.SeparatorType
import com.cocro.domain.grid.model.Cell
import com.cocro.domain.grid.model.CellPos
import com.cocro.domain.grid.model.Clue
import com.cocro.domain.grid.model.Grid
import com.cocro.domain.grid.model.GridMetadata
import com.cocro.domain.grid.model.Letter
import com.cocro.domain.grid.model.valueobject.ClueText
import com.cocro.domain.grid.model.valueobject.GridHeight
import com.cocro.domain.grid.model.valueobject.GridShareCode
import com.cocro.domain.grid.model.valueobject.GridTitle
import com.cocro.domain.grid.model.valueobject.GridWidth
import com.cocro.domain.grid.model.valueobject.LetterValue
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

class GridTemplateMapperTest {

    private val gridId = GridShareCode("GRID01")

    private fun buildGrid(vararg cells: Cell): Grid = Grid(
        id = UUID.randomUUID(),
        shortId = gridId,
        title = GridTitle("Ma Grille"),
        metadata = GridMetadata(
            author = com.cocro.domain.auth.model.valueobject.UserId.new(),
            reference = "REF-XYZ",
            description = "Description de test",
            difficulty = "3",
            globalClueLabel = "Mot mystère",
            globalClueWordLengths = listOf(2, 5),
        ),
        width = GridWidth(5),
        height = GridHeight(5),
        cells = cells.toList(),
    )

    @Test
    fun `should map grid metadata`() {
        val grid = buildGrid()
        val dto = grid.toGridTemplateDto()

        assertThat(dto.title).isEqualTo("Ma Grille")
        assertThat(dto.width).isEqualTo(5)
        assertThat(dto.height).isEqualTo(5)
        assertThat(dto.difficulty).isEqualTo("3")
        assertThat(dto.reference).isEqualTo("REF-XYZ")
        assertThat(dto.description).isEqualTo("Description de test")
        assertThat(dto.globalClueLabel).isEqualTo("Mot mystère")
        assertThat(dto.globalClueWordLengths).containsExactly(2, 5)
    }

    @Test
    fun `should strip letter value from LetterCell`() {
        val cell = Cell.LetterCell(
            pos = CellPos(2, 1),
            letter = Letter(value = LetterValue('A'), separator = SeparatorType.LEFT, number = 5),
        )
        val dto = buildGrid(cell).toGridTemplateDto()

        val cellDto = dto.cells.first()
        assertThat(cellDto.type).isEqualTo("LETTER")
        assertThat(cellDto.x).isEqualTo(2)
        assertThat(cellDto.y).isEqualTo(1)
        assertThat(cellDto.separator).isEqualTo("LEFT")
        assertThat(cellDto.number).isEqualTo(5)
        assertThat(cellDto.clues).isNull()
        // No letter value in the DTO — letters are stripped by design
    }

    @Test
    fun `should map LetterCell with NONE separator and no number`() {
        val cell = Cell.LetterCell(
            pos = CellPos(0, 0),
            letter = Letter(value = LetterValue('B'), separator = SeparatorType.NONE, number = null),
        )
        val dto = buildGrid(cell).toGridTemplateDto()

        val cellDto = dto.cells.first()
        assertThat(cellDto.separator).isEqualTo("NONE")
        assertThat(cellDto.number).isNull()
    }

    @Test
    fun `should map SingleClueCell with clue text and direction`() {
        val cell = Cell.ClueCell.SingleClueCell(
            pos = CellPos(0, 1),
            clue = Clue(direction = ClueDirection.RIGHT, text = ClueText("Définition simple")),
        )
        val dto = buildGrid(cell).toGridTemplateDto()

        val cellDto = dto.cells.first()
        assertThat(cellDto.type).isEqualTo("CLUE_SINGLE")
        assertThat(cellDto.separator).isNull()
        assertThat(cellDto.number).isNull()
        assertThat(cellDto.clues).hasSize(1)
        assertThat(cellDto.clues!![0].direction).isEqualTo("RIGHT")
        assertThat(cellDto.clues!![0].text).isEqualTo("Définition simple")
    }

    @Test
    fun `should map DoubleClueCell with both clues`() {
        val cell = Cell.ClueCell.DoubleClueCell(
            pos = CellPos(0, 2),
            first = Clue(direction = ClueDirection.RIGHT, text = ClueText("Première def")),
            second = Clue(direction = ClueDirection.DOWN, text = ClueText("Deuxième def")),
        )
        val dto = buildGrid(cell).toGridTemplateDto()

        val cellDto = dto.cells.first()
        assertThat(cellDto.type).isEqualTo("CLUE_DOUBLE")
        assertThat(cellDto.clues).hasSize(2)
        assertThat(cellDto.clues!![0].direction).isEqualTo("RIGHT")
        assertThat(cellDto.clues!![1].direction).isEqualTo("DOWN")
        assertThat(cellDto.clues!![1].text).isEqualTo("Deuxième def")
    }

    @Test
    fun `should map BlackCell with no clues and no separator`() {
        val cell = Cell.BlackCell(pos = CellPos(3, 2))
        val dto = buildGrid(cell).toGridTemplateDto()

        val cellDto = dto.cells.first()
        assertThat(cellDto.type).isEqualTo("BLACK")
        assertThat(cellDto.x).isEqualTo(3)
        assertThat(cellDto.y).isEqualTo(2)
        assertThat(cellDto.separator).isNull()
        assertThat(cellDto.number).isNull()
        assertThat(cellDto.clues).isNull()
    }

    @Test
    fun `should preserve cell order`() {
        val cells = listOf(
            Cell.ClueCell.SingleClueCell(pos = CellPos(0, 0), clue = Clue(ClueDirection.RIGHT, ClueText("X"))),
            Cell.LetterCell(pos = CellPos(1, 0), letter = Letter(LetterValue('C'), SeparatorType.NONE, 1)),
            Cell.BlackCell(pos = CellPos(2, 0)),
        )
        val dto = buildGrid(*cells.toTypedArray()).toGridTemplateDto()

        assertThat(dto.cells).hasSize(3)
        assertThat(dto.cells[0].type).isEqualTo("CLUE_SINGLE")
        assertThat(dto.cells[1].type).isEqualTo("LETTER")
        assertThat(dto.cells[2].type).isEqualTo("BLACK")
    }

    @Test
    fun `should return empty cells list for empty grid`() {
        val dto = buildGrid().toGridTemplateDto()
        assertThat(dto.cells).isEmpty()
    }
}
