package com.cocro.infrastructure.persistence.mongo.grid.repository

import com.cocro.infrastructure.persistence.mongo.grid.document.GridDocument
import org.springframework.data.mongodb.repository.MongoRepository
import java.util.UUID

interface SpringDataGridRepository : MongoRepository<GridDocument, UUID> {
    fun findByHashLetters(hashLetters: Long): GridDocument?

    fun existsByShortId(shortId: String): Boolean

    fun findByShortId(shortId: String): GridDocument?

    fun save(document: GridDocument): GridDocument
}
