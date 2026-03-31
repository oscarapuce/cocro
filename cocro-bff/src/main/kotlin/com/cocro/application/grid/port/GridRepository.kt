package com.cocro.application.grid.port

import com.cocro.domain.auth.model.valueobject.UserId
import com.cocro.domain.grid.model.Grid
import com.cocro.domain.grid.model.valueobject.GridShareCode

interface GridRepository {
    fun findByHashLetters(hash: Long): Grid?

    fun save(grid: Grid): Grid

    fun existsByShortId(shortId: GridShareCode): Boolean

    fun findByShortId(shortId: GridShareCode): Grid?

    fun findByAuthor(author: UserId): List<Grid>
}
