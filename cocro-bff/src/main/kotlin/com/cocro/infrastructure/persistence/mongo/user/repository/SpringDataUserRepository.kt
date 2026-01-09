package com.cocro.infrastructure.persistence.mongo.user.repository

import com.cocro.infrastructure.persistence.mongo.user.document.UserDocument
import org.springframework.data.mongodb.repository.MongoRepository

interface SpringDataUserRepository : MongoRepository<UserDocument, String> {
    fun findByUsername(username: String): UserDocument?

    fun save(user: UserDocument): UserDocument
}
