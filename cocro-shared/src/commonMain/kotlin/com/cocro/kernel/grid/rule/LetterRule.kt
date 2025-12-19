package com.cocro.kernel.grid.rule

import com.cocro.kernel.common.rule.CocroRule

object LetterRule: CocroRule<Char> {
    override fun validate(value: Char): Boolean {
        return value in 'A'..'Z'
    }
}