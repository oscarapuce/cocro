package com.cocro.infrastructure.persistence.mongo.grid.repository

import com.cocro.application.grid.port.GridRepository
import com.cocro.domain.auth.model.valueobject.UserId
import com.cocro.infrastructure.persistence.mongo.grid.mapper.toDocument
import com.cocro.infrastructure.persistence.mongo.grid.mapper.toDomain
import com.cocro.domain.grid.model.Grid
import com.cocro.domain.grid.model.valueobject.GridShareCode
import org.springframework.stereotype.Repository

@Repository
class MongoGridRepositoryAdapter(
    private val springDataRepo: SpringDataGridRepository,
) : GridRepository {
    override fun findByHashLetters(hash: Long): Grid? =
        springDataRepo
            .findByHashLetters(hash)
            ?.toDomain()

    override fun save(grid: Grid): Grid =
        springDataRepo
            .save(grid.toDocument())
            .toDomain()

    override fun existsByShortId(shortId: GridShareCode): Boolean = springDataRepo.existsByShortId(shortId.toString())

    override fun findByShortId(shortId: GridShareCode): Grid? =
        springDataRepo
            .findByShortId(shortId.toString())
            ?.toDomain()

    override fun findByAuthor(author: UserId): List<Grid> =
        springDataRepo
            .findByMetadataAuthorId(author.toString())
            .map { it.toDomain() }
}
