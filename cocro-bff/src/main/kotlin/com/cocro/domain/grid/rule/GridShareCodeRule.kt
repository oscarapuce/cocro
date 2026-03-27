package com.cocro.domain.grid.rule

import com.cocro.domain.common.rule.CocroRule

object GridShareCodeRule : CocroRule<String>() {
    const val GRID_SHARE_CODE_SIZE = 6
    const val GRID_SHARE_CODE_PATTERN = "[A-Z0-9]{$GRID_SHARE_CODE_SIZE}"

    override val arity: Int = 1

    override fun isValid(values: List<String>): Boolean = GRID_SHARE_CODE_PATTERN.toRegex().matches(values.first())
}
