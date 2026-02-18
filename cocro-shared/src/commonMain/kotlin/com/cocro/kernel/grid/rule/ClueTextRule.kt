package com.cocro.kernel.grid.rule

import com.cocro.kernel.common.rule.CocroRule

object ClueTextRule : CocroRule<String>() {
    const val MIN_LENGTH = 3
    const val MAX_LENGTH = 40

    override val arity: Int = 1

    override fun isValid(values: List<String>): Boolean {
        val text = values.first()
        return text.isNotBlank() && text.length in MIN_LENGTH..MAX_LENGTH
    }
}
