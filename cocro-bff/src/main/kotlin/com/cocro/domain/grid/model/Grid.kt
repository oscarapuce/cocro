package com.cocro.domain.grid.model

import com.cocro.domain.grid.model.valueobject.GridHeight
import com.cocro.domain.grid.model.valueobject.GridShareCode
import com.cocro.domain.grid.model.valueobject.GridTitle
import com.cocro.domain.grid.model.valueobject.GridWidth
import java.time.Instant
import java.util.UUID

data class Grid(
    val id: UUID,
    val shortId: GridShareCode,
    val title: GridTitle,
    val metadata: GridMetadata,
    val hashLetters: Long,
    val width: GridWidth,
    val height: GridHeight,
    val cells: List<Cell>,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    /**
     * Constructeur métier
     * Le hash des lettres est dérivé automatiquement
     */
    constructor(
        id: UUID,
        shortId: GridShareCode,
        title: GridTitle,
        metadata: GridMetadata,
        width: GridWidth,
        height: GridHeight,
        cells: List<Cell>,
        createdAt: Instant = Instant.now(),
        updatedAt: Instant = createdAt,
    ) : this(
        id = id,
        shortId = shortId,
        title = title,
        metadata = metadata,
        hashLetters = computeLetterHash(cells),
        width = width,
        height = height,
        cells = cells,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    companion object {
        private fun computeLetterHash(cells: List<Cell>): Long =
            cells
                .asSequence()
                .filterIsInstance<Cell.LetterCell>()
                .sortedWith(compareBy({ it.pos.y }, { it.pos.x }))
                .map { it.letter.value }
                .joinToString(separator = "")
                .hashCode()
                .toLong()
    }
}
