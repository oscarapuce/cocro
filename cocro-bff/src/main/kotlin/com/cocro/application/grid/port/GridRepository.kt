package com.cocro.application.grid.port

import com.cocro.domain.grid.model.Grid

interface GridRepository {
    fun findByHashLetters(hash: Long): Grid?

    fun save(grid: Grid): Grid

    fun existsByShortId(shortId: String): Boolean
}
