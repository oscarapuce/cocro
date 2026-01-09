package com.cocro.infrastructure.persistence.mongo.user.repository

import com.cocro.application.auth.port.UserRepository
import com.cocro.domain.auth.model.User
import com.cocro.domain.auth.valueobject.Username
import com.cocro.infrastructure.persistence.mongo.user.mapper.toDocument
import com.cocro.infrastructure.persistence.mongo.user.mapper.toDomain
import org.springframework.stereotype.Repository

@Repository
class MongoUserRepositoryAdapter(
    private val springRepo: SpringDataUserRepository,
) : UserRepository {
    override fun findByUsername(username: Username): User? =
        springRepo
            .findByUsername(username.value)
            ?.toDomain()

    override fun save(user: User): User = springRepo.save(user.toDocument()).toDomain()
}
