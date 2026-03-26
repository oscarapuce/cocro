package com.cocro.kernel.auth.model

import com.cocro.kernel.auth.enum.Role
import com.cocro.kernel.auth.model.valueobject.UserId

data class AuthenticatedUser(
    val userId: UserId,
    val roles: Set<Role>,
) : CocroUser {
    override fun userId(): String = userId.toString()

    override fun roles(): Set<String> = roles.map { it.toString() }.toSet()

    override fun isAdmin(): Boolean = roles.contains(Role.ADMIN)
}
