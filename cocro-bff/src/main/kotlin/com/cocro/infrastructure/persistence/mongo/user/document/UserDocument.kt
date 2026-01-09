package com.cocro.infrastructure.persistence.mongo.user.document

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document

@Document("users")
data class UserDocument(
    @Id
    val id: String,
    @Indexed(unique = true)
    val username: String,
    val passwordHash: String,
    val roles: Set<String>,
    val email: String?,
)
