package com.cocro.application.session.dto

data class GridTemplateDto(
    val title: String,
    val width: Int,
    val height: Int,
    val difficulty: String,
    val author: String,
    val reference: String?,
    val description: String?,
    val globalClueLabel: String?,
    val globalClueWordLengths: List<Int>?,
    val cells: List<GridTemplateCellDto>,
)

data class GridTemplateCellDto(
    val x: Int,
    val y: Int,
    val type: String,          // "LETTER" | "CLUE_SINGLE" | "CLUE_DOUBLE" | "BLACK"
    val separator: String?,    // "LEFT" | "UP" | "BOTH" | "NONE" — seulement pour LETTER
    val number: Int?,          // seulement pour LETTER
    val clues: List<GridTemplateClueDto>?,
)

data class GridTemplateClueDto(
    val direction: String,
    val text: String,
)
