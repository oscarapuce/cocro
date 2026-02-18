package com.cocro.infrastructure.security.spring

import com.cocro.application.auth.port.CurrentUserProvider
import com.cocro.domain.auth.model.AuthenticatedUser
import com.cocro.domain.auth.model.valueobject.UserId
import com.cocro.kernel.auth.enum.Role
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component

@Component
class PrincipalCurrentUserProvider : CurrentUserProvider {
    override fun currentUserOrNull(): AuthenticatedUser? {
        val auth = SecurityContextHolder.getContext().authentication ?: return null

        val jwt =
            auth.principal as? org.springframework.security.oauth2.jwt.Jwt
                ?: return null

        val userId = UserId.from(jwt.subject)

        val roles =
            jwt
                .getClaimAsStringList("roles")
                ?.map { Role.valueOf(it) }
                ?.toSet()
                ?: emptySet()

        return AuthenticatedUser(
            userId = userId,
            roles = roles,
        )
    }
}
