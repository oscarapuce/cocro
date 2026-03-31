package com.cocro.domain.grid.model

import com.cocro.domain.grid.model.valueobject.GridDimension
import com.cocro.domain.grid.model.valueobject.GridHeight
import com.cocro.domain.grid.model.valueobject.GridShareCode
import com.cocro.domain.grid.model.valueobject.GridTitle
import com.cocro.domain.grid.model.valueobject.GridWidth
import java.time.Instant
import java.util.UUID

data class Grid(
    val id: UUID,
    val shortId: GridShareCode,
    val metadata: GridMetadata,
    val hashLetters: Long,
    val dimension: GridDimension,
    val cells: List<Cell>,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    // Helpers d'accès rapide (rétro-compat)
    val title: GridTitle get() = metadata.title
    val width: GridWidth get() = dimension.width
    val height: GridHeight get() = dimension.height

    /**
     * Constructeur métier
     * Le hash des lettres est dérivé automatiquement
     */
    constructor(
        id: UUID,
        shortId: GridShareCode,
        metadata: GridMetadata,
        dimension: GridDimension,
        cells: List<Cell>,
        createdAt: Instant = Instant.now(),
        updatedAt: Instant = createdAt,
    ) : this(
        id = id,
        shortId = shortId,
        metadata = metadata,
        hashLetters = computeLetterHash(cells),
        dimension = dimension,
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
                .map { it.letter.value.value }
                .joinToString(separator = "")
                .hashCode()
                .toLong()
    }
}
