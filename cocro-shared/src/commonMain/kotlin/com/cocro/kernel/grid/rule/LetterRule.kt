package com.cocro.kernel.grid.rule

import com.cocro.kernel.common.rule.CocroRule

object LetterRule : CocroRule<Char> {
    override val arity: Int = 1

    override fun isValid(values: List<Char>): Boolean {
        val letter = values.first()
        return letter in 'A'..'Z'
    }
}
