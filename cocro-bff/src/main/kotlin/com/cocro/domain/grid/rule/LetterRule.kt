package com.cocro.domain.grid.rule

import com.cocro.domain.common.rule.CocroRule

object LetterRule : CocroRule<Char>() {
    override val arity: Int = 1

    override fun isValid(values: List<Char>): Boolean {
        val letter = values.first()
        return letter.isLetter() && letter in 'A'..'Z'
    }
}
