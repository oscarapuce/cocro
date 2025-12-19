package com.cocro.domain.grid.valueobject

import com.cocro.kernel.grid.rule.GridWidthRule

@JvmInline
value class GridWidth(
    val value: Int,
) {
    init {
        require(GridWidthRule.validate(value)) {
            "Grid width must be between ${GridWidthRule.MIN_WIDTH} and ${GridWidthRule.MAX_WIDTH}"
        }
    }
}
