package com.cocro.infrastructure.persistence.mongo.session.repository

import com.cocro.application.session.port.SessionRepository
import com.cocro.infrastructure.persistence.mongo.session.document.SessionDocument
import com.cocro.infrastructure.persistence.mongo.session.mapper.toDocument
import com.cocro.infrastructure.persistence.mongo.session.mapper.toDomain
import com.cocro.domain.session.model.Session
import com.cocro.domain.session.model.state.SessionGridState
import com.cocro.domain.session.model.valueobject.SessionId
import com.cocro.domain.session.model.valueobject.SessionShareCode
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Repository

@Repository
class MongoSessionRepositoryAdapter(
    private val springDataRepo: SpringDataSessionRepository,
    private val mongoTemplate: MongoTemplate,
) : SessionRepository {

    override fun findByShareCode(code: SessionShareCode): Session? =
        springDataRepo.findByShareCode(code.toString())?.toDomain()

    override fun findById(sessionId: SessionId): Session? =
        springDataRepo.findById(sessionId.value).orElse(null)?.toDomain()

    override fun existsByShareCode(code: SessionShareCode): Boolean =
        springDataRepo.existsByShareCode(code.toString())

    override fun save(session: Session): Session =
        springDataRepo.save(session.toDocument()).toDomain()

    override fun updateGridState(sessionId: SessionId, gridState: SessionGridState) {
        mongoTemplate.updateFirst(
            Query.query(Criteria.where("_id").`is`(sessionId.value)),
            Update().set("sessionGridState", gridState.toDocument()),
            SessionDocument::class.java,
        )
    }
}
