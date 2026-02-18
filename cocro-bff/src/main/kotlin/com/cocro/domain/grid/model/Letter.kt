package com.cocro.domain.grid.model

import com.cocro.domain.grid.model.valueobject.LetterValue
import com.cocro.kernel.grid.enums.SeparatorType

data class Letter(
    val value: LetterValue,
    val separator: SeparatorType = SeparatorType.NONE,
    val number: Int? = null,
)
