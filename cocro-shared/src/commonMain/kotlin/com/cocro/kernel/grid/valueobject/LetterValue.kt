package com.cocro.kernel.grid.valueobject

import com.cocro.kernel.grid.rule.LetterRule

@JvmInline
value class LetterValue(val value: Char) {
    init {
        require(LetterRule.validate(value)) {
            "Cell letter value must be a single uppercase letter A-Z"
        }
    }
}