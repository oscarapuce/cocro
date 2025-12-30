package com.cocro.infrastructure.persistence.mongo.grid.document

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant
import java.util.UUID

@Document(collection = "grids")
@CompoundIndex(
    name = "shortId_hashLetters_idx",
    def = "{'shortId': 1, 'hashLetters': 1}",
    unique = true
)
data class GridDocument(

    @Id
    val id: UUID,

    @Indexed(unique = true)
    val shortId: String,

    val title: String,

    val metadata: GridMetadataDocument,

    @Indexed
    val hashLetters: Long,

    val width: Int,
    val height: Int,

    val cells: List<CellDocument>,

    val createdAt: Instant,
    val updatedAt: Instant,
)
