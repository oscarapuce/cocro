package com.cocro.domain.grid.model

import com.cocro.kernel.grid.enums.GridDifficulty

class GridMetadata(
    val author: String?,
    val reference: String?,
    val description: String?,
    val difficulty: GridDifficulty,
)
