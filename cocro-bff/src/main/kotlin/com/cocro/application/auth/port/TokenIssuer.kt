package com.cocro.application.auth.port

import com.cocro.domain.auth.enum.Role
import com.cocro.domain.auth.model.valueobject.UserId

interface TokenIssuer {
    fun issue(
        userId: UserId,
        username: String,
        roles: Set<Role>,
    ): String
}
