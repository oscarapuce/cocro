package com.cocro.presentation.websocket

import com.cocro.kernel.auth.model.AuthenticatedUser
import java.security.Principal

class CocroPrincipal(
    private val user: AuthenticatedUser,
) : Principal {
    override fun getName(): String = user.userId

    fun userId() = user.userId

    fun roles() = user.roles
}
