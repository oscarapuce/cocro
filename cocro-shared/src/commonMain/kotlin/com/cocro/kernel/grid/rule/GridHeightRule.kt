package com.cocro.kernel.grid.rule

import com.cocro.kernel.common.rule.CocroRule

object GridHeightRule: CocroRule<Int> {
    const val MIN_HEIGHT = 5
    const val MAX_HEIGHT = 50

    override fun validate(value: Int): Boolean {
        return value in MIN_HEIGHT..MAX_HEIGHT
    }
}