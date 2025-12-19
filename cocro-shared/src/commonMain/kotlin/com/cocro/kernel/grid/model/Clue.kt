package com.cocro.kernel.grid.model

import com.cocro.kernel.grid.model.enums.ClueDirection
import com.cocro.kernel.grid.valueobject.ClueText

data class Clue(
    val direction: ClueDirection,
    val text: ClueText,
)