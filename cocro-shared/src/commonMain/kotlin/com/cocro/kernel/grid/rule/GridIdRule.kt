package com.cocro.kernel.grid.rule

import com.cocro.kernel.common.rule.CocroRule

object GridIdRule : CocroRule<String> {
    const val GRID_ID_REGEX = "[A-Z0-9]{6}"

    override val arity: Int = 1

    override fun isValid(values: List<String>): Boolean {
        val gridId = values.first()
        return GRID_ID_REGEX.toRegex().matches(gridId)
    }
}
