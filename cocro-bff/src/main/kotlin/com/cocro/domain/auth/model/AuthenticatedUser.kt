package com.cocro.domain.auth.model

import com.cocro.domain.auth.model.valueobject.UserId
import com.cocro.kernel.auth.enum.Role
import com.cocro.kernel.auth.model.CocroUser

data class AuthenticatedUser(
    val userId: UserId,
    val roles: Set<Role>,
) : CocroUser {
    override fun userId(): String = userId.toString()

    override fun roles(): Set<String> = roles.map { it.toString() }.toSet()

    override fun isAdmin(): Boolean = roles.contains(Role.ADMIN)
}
