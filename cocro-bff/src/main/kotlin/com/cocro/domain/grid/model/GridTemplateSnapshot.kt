package com.cocro.domain.grid.model

import com.cocro.domain.grid.model.valueobject.GridShareCode

data class GridTemplateSnapshot(
    val shortId: GridShareCode,
    val title: String,
    val width: Int,
    val height: Int,
    val difficulty: String?,
    val author: String?,
    val reference: String?,
    val description: String?,
    val globalClueLabel: String?,
    val globalClueWordLengths: List<Int>?,
    val cells: List<GridTemplateCellSnapshot>,
)
