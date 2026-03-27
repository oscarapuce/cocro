package com.cocro.domain.grid.model

import com.cocro.domain.grid.enums.ClueDirection
import com.cocro.domain.grid.model.valueobject.ClueText

data class Clue(
    val direction: ClueDirection,
    val text: ClueText,
)
