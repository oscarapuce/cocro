package com.cocro.kernel.grid.model

import com.cocro.kernel.auth.model.valueobject.UserId
import com.cocro.kernel.grid.enums.GridDifficulty

data class GridMetadata(
    val author: UserId,
    val reference: String?,
    val description: String?,
    val difficulty: GridDifficulty,
)
