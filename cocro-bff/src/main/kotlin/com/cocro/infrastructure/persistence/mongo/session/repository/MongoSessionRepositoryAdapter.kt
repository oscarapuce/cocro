package com.cocro.infrastructure.persistence.mongo.session.repository

import com.cocro.application.session.port.SessionRepository
import com.cocro.infrastructure.persistence.mongo.session.document.SessionDocument
import com.cocro.infrastructure.persistence.mongo.session.mapper.toDocument
import com.cocro.infrastructure.persistence.mongo.session.mapper.toDomain
import com.cocro.domain.auth.model.valueobject.UserId
import com.cocro.domain.grid.model.valueobject.GridShareCode
import com.cocro.domain.session.model.Session
import com.cocro.domain.session.model.state.SessionGridState
import com.cocro.domain.session.model.valueobject.SessionId
import com.cocro.domain.session.model.valueobject.SessionShareCode
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Repository
import java.time.Instant

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

    override fun findByCreator(authorId: UserId): List<Session> =
        springDataRepo.findByAuthorId(authorId.toString()).map { it.toDomain() }

    override fun findByParticipantUserId(userId: UserId): List<Session> =
        springDataRepo.findByParticipantsUserId(userId.toString()).map { it.toDomain() }

    override fun deleteById(sessionId: SessionId) {
        springDataRepo.deleteById(sessionId.value.toString())
    }

    override fun countActiveByUser(userId: UserId): Int {
        val activeStatuses = listOf("PLAYING", "INTERRUPTED")
        val userIdStr = userId.toString()
        val query = Query.query(
            Criteria().andOperator(
                Criteria.where("status").`in`(activeStatuses),
                Criteria().orOperator(
                    Criteria.where("authorId").`is`(userIdStr),
                    Criteria.where("participants").elemMatch(
                        Criteria.where("userId").`is`(userIdStr).and("status").`is`("JOINED"),
                    ),
                ),
            ),
        )
        return mongoTemplate.count(query, SessionDocument::class.java).toInt()
    }

    override fun findInterruptedBefore(cutoff: Instant): List<Session> {
        val query = Query.query(
            Criteria.where("status").`is`("INTERRUPTED")
                .and("updatedAt").lt(cutoff),
        )
        return mongoTemplate.find(query, SessionDocument::class.java).map { it.toDomain() }
    }

    override fun existsByGridIdAndActiveStatus(gridId: GridShareCode): Boolean {
        val activeStatuses = listOf("PLAYING", "INTERRUPTED")
        val query = Query.query(
            Criteria.where("status").`in`(activeStatuses)
                .and("gridShortId").`is`(gridId.value),
        )
        return mongoTemplate.exists(query, SessionDocument::class.java)
    }
}
