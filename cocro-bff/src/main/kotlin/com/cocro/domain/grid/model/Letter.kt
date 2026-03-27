package com.cocro.domain.grid.model

import com.cocro.domain.grid.enums.SeparatorType
import com.cocro.domain.grid.model.valueobject.LetterValue

data class Letter(
    val value: LetterValue,
    val separator: SeparatorType = SeparatorType.NONE,
    val number: Int? = null,
)
