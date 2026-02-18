package com.cocro.application.auth.port

import com.cocro.domain.auth.model.valueobject.UserId
import com.cocro.kernel.auth.enum.Role

interface TokenIssuer {
    fun issue(
        userId: UserId,
        roles: Set<Role>,
    ): String
}
