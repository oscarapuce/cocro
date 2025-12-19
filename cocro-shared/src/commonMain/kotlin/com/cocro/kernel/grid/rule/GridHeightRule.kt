package com.cocro.kernel.grid.rule

import com.cocro.kernel.common.rule.CocroRule

object GridHeightRule : CocroRule<Int> {
    const val MIN_HEIGHT = 5
    const val MAX_HEIGHT = 50

    override val arity: Int = 1

    override fun isValid(values: List<Int>): Boolean {
        val height = values.first()
        return height in MIN_HEIGHT..MAX_HEIGHT
    }
}
