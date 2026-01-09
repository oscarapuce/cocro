package com.cocro.presentation.websocket

import com.cocro.domain.auth.model.AuthenticatedUser
import java.security.Principal

class CocroPrincipal(
    private val user: AuthenticatedUser,
) : Principal {
    override fun getName(): String = user.userId.toString()

    fun userId() = user.userId

    fun roles() = user.roles
}
