package com.cocro.kernel.grid.model

import com.cocro.kernel.auth.model.valueobject.UserId

data class GridMetadata(
    val author: UserId,
    val reference: String?,
    val description: String?,
    val difficulty: String = "NONE",
    val globalClueLabel: String? = null,
    val globalClueWords: List<List<Int>>? = null,
)
