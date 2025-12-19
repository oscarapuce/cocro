package com.cocro.kernel.grid.rule

import com.cocro.kernel.common.rule.CocroRule

object GridWidthRule: CocroRule<Int> {
    const val MIN_WIDTH = 5
    const val MAX_WIDTH = 70

    override fun validate(value: Int): Boolean {
        return value in MIN_WIDTH..MAX_WIDTH
    }
}