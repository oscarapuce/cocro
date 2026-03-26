package com.cocro.kernel.grid.rule

import com.cocro.kernel.common.rule.CocroRule

object GridWidthRule : CocroRule<Int>() {
    const val MIN_WIDTH = 5
    const val MAX_WIDTH = 70

    override val arity: Int = 1

    override fun isValid(values: List<Int>): Boolean {
        val width = values.first()
        return width in MIN_WIDTH..MAX_WIDTH
    }
}
