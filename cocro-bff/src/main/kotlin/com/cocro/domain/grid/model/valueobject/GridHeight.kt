package com.cocro.domain.grid.model.valueobject

import com.cocro.kernel.grid.rule.GridHeightRule

@JvmInline
value class GridHeight(
    val value: Int,
) {
    init {
        require(GridHeightRule.validate(value)) {
            "Grid width must be between ${GridHeightRule.MIN_HEIGHT} and ${GridHeightRule.MAX_HEIGHT}"
        }
    }
}
