package com.cocro.domain.grid.model.valueobject

import com.cocro.kernel.grid.rule.GridTitleRule

@JvmInline
value class GridTitle(
    val value: String,
) {
    init {
        require(GridTitleRule.validate(value)) {
            "Grid title must not blank and max ${GridTitleRule.TITLE_MAX_LENGTH} characters"
        }
    }
}
