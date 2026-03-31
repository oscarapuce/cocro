package com.cocro.infrastructure.persistence.mongo.session.repository

import com.cocro.infrastructure.persistence.mongo.session.document.SessionDocument
import org.springframework.data.mongodb.repository.MongoRepository
import java.util.UUID

interface SpringDataSessionRepository : MongoRepository<SessionDocument, UUID> {
    fun findByShareCode(shareCode: String): SessionDocument?

    fun existsByShareCode(shareCode: String): Boolean

    fun save(session: SessionDocument): SessionDocument

    fun findByAuthorId(authorId: String): List<SessionDocument>

    fun findByParticipantsUserId(userId: String): List<SessionDocument>

    fun deleteById(id: String)
}
