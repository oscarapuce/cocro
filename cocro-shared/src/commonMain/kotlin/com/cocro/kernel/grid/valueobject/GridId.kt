package com.cocro.kernel.grid.valueobject

import com.cocro.kernel.grid.rule.GridIdRule

@JvmInline
value class GridId(val value: String) {
    init {
        require(GridIdRule.validate(value)) {
            "Grid ID must be a 6-character alphanumeric string (A-Z, 0-9)"
        }
    }
}
