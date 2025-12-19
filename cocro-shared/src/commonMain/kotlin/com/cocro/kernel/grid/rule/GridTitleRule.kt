package com.cocro.kernel.grid.rule

import com.cocro.kernel.common.rule.CocroRule

object GridTitleRule: CocroRule<String> {
    const val MAX_LENGTH = 60

    override fun validate(value: String): Boolean {
        return value .isNotBlank() && value.length <= MAX_LENGTH
    }
}