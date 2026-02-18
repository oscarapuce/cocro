package com.cocro.domain.grid.model

import com.cocro.domain.auth.model.valueobject.UserId
import com.cocro.kernel.grid.enums.GridDifficulty

data class GridMetadata(
    val author: UserId,
    val reference: String?,
    val description: String?,
    val difficulty: GridDifficulty,
)
