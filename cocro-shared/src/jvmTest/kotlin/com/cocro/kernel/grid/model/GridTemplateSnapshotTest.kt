package com.cocro.kernel.grid.model

import com.cocro.kernel.grid.model.valueobject.GridShareCode
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class GridTemplateSnapshotTest {

    @Test
    fun `GridTemplateSnapshot holds cell list without letters`() {
        val cell = GridTemplateCellSnapshot(
            x = 0, y = 0, type = "LETTER",
            separator = null, number = 1, clues = null
        )
        val snapshot = GridTemplateSnapshot(
            gridShortId = GridShareCode("ABC123"),
            title = "Test", width = 5, height = 5,
            difficulty = null, author = null, reference = null,
            description = null, globalClueLabel = null,
            globalClueWordLengths = null, cells = listOf(cell)
        )
        assertThat(snapshot.cells).hasSize(1)
        assertThat(snapshot.cells[0].type).isEqualTo("LETTER")
    }

    @Test
    fun `GridTemplateCellSnapshot stores clues`() {
        val clue = GridTemplateCellClueSnapshot(direction = "ACROSS", text = "A clue")
        val cell = GridTemplateCellSnapshot(
            x = 1, y = 2, type = "LETTER",
            separator = null, number = 5, clues = listOf(clue)
        )
        assertThat(cell.clues).hasSize(1)
        assertThat(cell.clues!![0].direction).isEqualTo("ACROSS")
    }
}
