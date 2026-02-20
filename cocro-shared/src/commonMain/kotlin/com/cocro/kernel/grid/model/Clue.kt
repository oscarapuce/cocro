package com.cocro.kernel.grid.model

import com.cocro.kernel.grid.enums.ClueDirection
import com.cocro.kernel.grid.model.valueobject.ClueText

data class Clue(
    val direction: ClueDirection,
    val text: ClueText,
)
