package com.cocro.kernel.grid.model

import com.cocro.kernel.grid.model.enums.SeparatorType
import com.cocro.kernel.grid.valueobject.LetterValue

data class Letter(
    val value: LetterValue,
    val separator: SeparatorType = SeparatorType.NONE,
    val number: Int? = null,
)