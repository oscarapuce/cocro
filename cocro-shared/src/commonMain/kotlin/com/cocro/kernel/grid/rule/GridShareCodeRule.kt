package com.cocro.kernel.grid.rule

import com.cocro.kernel.common.rule.CocroRule

object GridShareCodeRule : CocroRule<String>() {
    const val GRID_SHARE_CODE_PATTENR = "[A-Z0-9]{6}"

    override val arity: Int = 1

    override fun isValid(values: List<String>): Boolean {
        val gridShareCode = values.first()
        return GRID_SHARE_CODE_PATTENR.toRegex().matches(gridShareCode)
    }
}
