package com.cocro.infrastructure.persistence.mongo.user.repository

import com.cocro.application.auth.port.UserRepository
import com.cocro.infrastructure.persistence.mongo.user.mapper.toDocument
import com.cocro.infrastructure.persistence.mongo.user.mapper.toDomain
import com.cocro.kernel.auth.model.User
import com.cocro.kernel.auth.model.valueobject.Username
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
