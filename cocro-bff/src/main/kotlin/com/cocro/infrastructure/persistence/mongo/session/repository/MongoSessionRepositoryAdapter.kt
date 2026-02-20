package com.cocro.infrastructure.persistence.mongo.session.repository

import com.cocro.application.session.port.SessionRepository
import com.cocro.infrastructure.persistence.mongo.session.mapper.toDocument
import com.cocro.infrastructure.persistence.mongo.session.mapper.toDomain
import com.cocro.kernel.session.model.Session
import com.cocro.kernel.session.model.valueobject.SessionShareCode
import org.springframework.stereotype.Repository

@Repository
class MongoSessionRepositoryAdapter(
    private val springDataRepo: SpringDataSessionRepository,
) : SessionRepository {
    override fun findByShareCode(code: SessionShareCode): Session? = springDataRepo.findByShareCode(code.toString())?.toDomain()

    override fun existsByShareCode(code: SessionShareCode): Boolean = springDataRepo.existsByShareCode(code.toString())

    override fun save(session: Session): Session = springDataRepo.save(session.toDocument()).toDomain()
}
