package com.cocro.infrastructure.persistence.mongo.grid.document

data class GridMetadataDocument(
    val author: String,
    val reference: String?,
    val description: String?,
    val difficulty: String = "NONE",
    val globalClueLabel: String? = null,
    val globalClueWordLengths: List<Int>? = null,
)
