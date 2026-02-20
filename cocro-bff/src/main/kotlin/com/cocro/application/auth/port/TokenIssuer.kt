package com.cocro.application.auth.port

import com.cocro.kernel.auth.enum.Role
import com.cocro.kernel.auth.model.valueobject.UserId

interface TokenIssuer {
    fun issue(
        userId: UserId,
        roles: Set<Role>,
    ): String
}
