package com.cocro.kernel.grid.rule

import com.cocro.kernel.common.rule.CocroRule

object GridIdRule: CocroRule<String> {
    const val GRID_ID_REGEX = "[A-Z0-9]{6}"

    override fun validate(value: String): Boolean {
        return GRID_ID_REGEX.toRegex().matches(value)
    }
}