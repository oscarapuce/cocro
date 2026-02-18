package com.cocro.domain.grid.model

import com.cocro.domain.grid.model.valueobject.ClueText
import com.cocro.kernel.grid.enums.ClueDirection

data class Clue(
    val direction: ClueDirection,
    val text: ClueText,
)
