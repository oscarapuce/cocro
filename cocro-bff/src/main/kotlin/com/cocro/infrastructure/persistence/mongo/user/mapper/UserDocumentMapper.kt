package com.cocro.infrastructure.persistence.mongo.user.mapper

import com.cocro.infrastructure.persistence.mongo.user.document.UserDocument
import com.cocro.domain.auth.enum.Role
import com.cocro.domain.auth.model.User
import com.cocro.domain.auth.model.valueobject.Email
import com.cocro.domain.auth.model.valueobject.PasswordHash
import com.cocro.domain.auth.model.valueobject.UserId
import com.cocro.domain.auth.model.valueobject.Username
import java.util.UUID

fun User.toDocument(): UserDocument =
    UserDocument(
        id = this.id.toString(),
        username = this.username.toString(),
        passwordHash = this.passwordHash.value,
        roles = this.roles.map { it.name }.toSet(),
        email = this.email?.toString(),
    )

fun UserDocument.toDomain(): User =
    User.reconstitute(
        id = UserId(UUID.fromString(this.id)),
        username = Username(this.username),
        passwordHash = PasswordHash(this.passwordHash),
        roles = this.roles.map { Role.valueOf(it) }.toSet(),
        email = this.email?.let { Email(it) },
    )
