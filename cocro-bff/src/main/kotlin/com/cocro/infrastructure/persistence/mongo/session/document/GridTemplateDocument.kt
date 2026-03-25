package com.cocro.infrastructure.persistence.mongo.session.document

data class GridTemplateDocument(
    val gridShortId: String,
    val title: String,
    val width: Int,
    val height: Int,
    val difficulty: String?,
    val author: String?,
    val reference: String?,
    val description: String?,
    val globalClueLabel: String?,
    val globalClueWordLengths: List<Int>?,
    val cells: List<GridTemplateCellDocument>,
)

data class GridTemplateCellDocument(
    val x: Int,
    val y: Int,
    val type: String,
    val separator: String?,
    val number: Int?,
    val clues: List<GridTemplateCellClueDocument>?,
)

data class GridTemplateCellClueDocument(
    val direction: String,
    val text: String,
)
