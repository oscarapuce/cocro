package com.cocro.application.grid.port

import com.cocro.kernel.grid.model.Grid
import com.cocro.kernel.grid.model.valueobject.GridShareCode

interface GridRepository {
    fun findByHashLetters(hash: Long): Grid?

    fun save(grid: Grid): Grid

    fun existsByShortId(shortId: GridShareCode): Boolean

    fun findByShortId(shortId: GridShareCode): Grid?
}
