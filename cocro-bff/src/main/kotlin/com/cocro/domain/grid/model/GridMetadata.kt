package com.cocro.domain.grid.model

import com.cocro.domain.common.model.Author
import com.cocro.domain.grid.model.valueobject.GridTitle

data class GridMetadata(
    val title: GridTitle,
    val author: Author,
    val reference: String?,
    val description: String?,
    val difficulty: String = "NONE",
    val globalClue: GlobalClue? = null,
)
