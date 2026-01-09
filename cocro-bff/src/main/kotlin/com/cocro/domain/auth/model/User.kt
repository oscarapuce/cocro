package com.cocro.domain.auth.model

import com.cocro.domain.auth.valueobject.Email
import com.cocro.domain.auth.valueobject.PasswordHash
import com.cocro.domain.auth.valueobject.UserId
import com.cocro.domain.auth.valueobject.Username
import com.cocro.kernel.auth.enum.Role

class User private constructor(
    val id: UserId,
    val username: Username,
    val passwordHash: PasswordHash,
    val roles: Set<Role>,
    val email: Email? = null,
) {
    companion object {
        fun register(
            username: Username,
            passwordHash: PasswordHash,
            email: Email?,
        ): User =
            User(
                id = UserId.new(),
                username = username,
                passwordHash = passwordHash,
                roles = setOf(Role.PLAYER),
                email = email,
            )

        fun reconstitute(
            id: UserId,
            username: Username,
            passwordHash: PasswordHash,
            roles: Set<Role>,
            email: Email?,
        ): User =
            User(
                id = id,
                username = username,
                passwordHash = passwordHash,
                roles = roles,
                email = email,
            )
    }

    fun isAdmin(): Boolean = roles.contains(Role.ADMIN)

    fun hasRole(role: Role): Boolean = roles.contains(role)
}
