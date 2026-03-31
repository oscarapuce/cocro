package com.cocro.infrastructure.security.spring

import com.cocro.application.auth.port.CurrentUserProvider
import com.cocro.domain.auth.enum.Role
import com.cocro.domain.auth.model.AuthenticatedUser
import com.cocro.domain.auth.model.valueobject.UserId
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component

@Component
class PrincipalCurrentUserProvider : CurrentUserProvider {
    override fun currentUserOrNull(): AuthenticatedUser? {
        val auth = SecurityContextHolder.getContext().authentication ?: return null

        return when (val principal = auth.principal) {
            // HTTP request authenticated via JWT resource server
            is org.springframework.security.oauth2.jwt.Jwt -> {
                val userId = UserId.from(principal.subject)
                val roles =
                    principal
                        .getClaimAsStringList("roles")
                        ?.map { Role.valueOf(it) }
                        ?.toSet()
                        ?: emptySet()
                AuthenticatedUser(userId = userId, username = principal.getClaimAsString("username") ?: "", roles = roles)
            }
            // WebSocket/STOMP request authenticated via CocroAuthentication
            is AuthenticatedUser -> principal
            else -> null
        }
    }
}
