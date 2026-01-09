package com.cocro.infrastructure.persistence.mongo.grid.document

data class CellDocument(
    val x: Int,
    val y: Int,
    val type: String,
    // Letter
    val letter: Char? = null,
    val separator: String? = null,
    val number: Int? = null,
    // Clues
    val clueDirection: String? = null,
    val clueText: String? = null,
    val secondClueDirection: String? = null,
    val secondClueText: String? = null,
)
