package com.cocro.domain.grid.model

data class GridTemplateCellSnapshot(
    val x: Int,
    val y: Int,
    val type: String,
    val separator: String?,
    val number: Int?,
    val clues: List<GridTemplateCellClueSnapshot>?,
)

data class GridTemplateCellClueSnapshot(
    val direction: String,
    val text: String,
)
