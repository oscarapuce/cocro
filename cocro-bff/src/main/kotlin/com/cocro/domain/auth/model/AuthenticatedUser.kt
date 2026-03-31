package com.cocro.domain.auth.model

import com.cocro.domain.auth.enum.Role
import com.cocro.domain.auth.model.valueobject.UserId

data class AuthenticatedUser(
    val userId: UserId,
    val username: String,
    val roles: Set<Role>,
) : CocroUser {
    override fun userId(): String = userId.toString()

    override fun roles(): Set<String> = roles.map { it.toString() }.toSet()

    override fun isAdmin(): Boolean = roles.contains(Role.ADMIN)
}
