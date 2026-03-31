package com.cocro.infrastructure.persistence.mongo.grid.document

data class GridMetadataDocument(
    val authorId: String? = null,
    val authorUsername: String? = null,
    val author: String? = null,  // legacy field (UUID string)
    val reference: String?,
    val description: String?,
    val difficulty: String = "NONE",
    val globalClueLabel: String? = null,
    val globalClueWordLengths: List<Int>? = null,
)
