package com.cocro.domain.auth.model

import com.cocro.domain.auth.model.valueobject.Email
import com.cocro.domain.auth.model.valueobject.PasswordHash
import com.cocro.domain.auth.model.valueobject.UserId
import com.cocro.domain.auth.model.valueobject.Username
import com.cocro.kernel.auth.enum.Role
import com.cocro.kernel.auth.model.CocroUser

class User private constructor(
    val id: UserId,
    val username: Username,
    val passwordHash: PasswordHash,
    val roles: Set<Role>,
    val email: Email? = null,
) : CocroUser {
    override fun userId(): String = id.toString()

    override fun roles(): Set<String> = roles.map { it.name }.toSet()

    override fun isAdmin(): Boolean = roles.contains(Role.ADMIN)

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
}
