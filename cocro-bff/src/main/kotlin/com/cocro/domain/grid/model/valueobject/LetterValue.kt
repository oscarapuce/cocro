package com.cocro.domain.grid.model.valueobject

import com.cocro.domain.grid.rule.LetterRule

@JvmInline
value class LetterValue(
    val value: Char,
) {
    init {
        require(LetterRule.validate(value)) {
            "Cell letter value must be a single uppercase letter A-Z"
        }
    }
}
