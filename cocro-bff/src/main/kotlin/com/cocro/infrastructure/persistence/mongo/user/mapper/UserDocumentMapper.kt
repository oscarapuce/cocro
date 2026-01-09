package com.cocro.infrastructure.persistence.mongo.user.mapper

import com.cocro.domain.auth.model.User
import com.cocro.domain.auth.valueobject.Email
import com.cocro.domain.auth.valueobject.PasswordHash
import com.cocro.domain.auth.valueobject.UserId
import com.cocro.domain.auth.valueobject.Username
import com.cocro.infrastructure.persistence.mongo.user.document.UserDocument
import com.cocro.kernel.auth.enum.Role
import java.util.UUID

fun User.toDocument(): UserDocument =
    UserDocument(
        id = this.id.toString(),
        username = this.username.value,
        passwordHash = this.passwordHash.value,
        roles = this.roles.map { it.toString() }.toSet(),
        email = this.email?.value,
    )

fun UserDocument.toDomain(): User =
    User.reconstitute(
        id = UserId(UUID.fromString(this.id)),
        username = Username(this.username),
        passwordHash = PasswordHash(this.passwordHash),
        roles = this.roles.map { Role.valueOf(it) }.toSet(),
        email = this.email?.let { Email(it) },
    )
