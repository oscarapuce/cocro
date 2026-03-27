package com.cocro.domain.grid.rule

import com.cocro.domain.common.rule.CocroRule

object GridTitleRule : CocroRule<String>() {
    const val TITLE_MAX_LENGTH = 60
    const val TITLE_MIN_LENGTH = 5

    override val arity: Int = 1

    override fun isValid(values: List<String>): Boolean {
        val title = values.first()
        return title.isNotBlank() &&
            title.length in TITLE_MIN_LENGTH..TITLE_MAX_LENGTH
    }
}
