package com.cocro.domain.grid.model

import com.cocro.domain.auth.model.valueobject.UserId

data class GridMetadata(
    val author: UserId,
    val reference: String?,
    val description: String?,
    val difficulty: String = "NONE",
    val globalClueLabel: String? = null,
    val globalClueWordLengths: List<Int>? = null,
)
