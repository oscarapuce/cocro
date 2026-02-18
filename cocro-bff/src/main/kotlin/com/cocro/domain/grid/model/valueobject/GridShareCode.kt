package com.cocro.domain.grid.model.valueobject

import com.cocro.kernel.grid.rule.GridShareCodeRule

@JvmInline
value class GridShareCode(
    val value: String,
) {
    init {
        require(GridShareCodeRule.validate(value)) {
            "Grid ID must be a 6-character alphanumeric string (A-Z, 0-9)"
        }
    }
}
