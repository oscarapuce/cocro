package com.cocro.kernel.grid.rule

import com.cocro.kernel.common.rule.CocroRule

object ClueTextRule: CocroRule<String> {

    const val MIN_LENGTH = 3
    const val MAX_LENGTH = 30

    override fun validate(value: String): Boolean {
        return value.isNotBlank() && value.length in MIN_LENGTH..MAX_LENGTH
    }
}