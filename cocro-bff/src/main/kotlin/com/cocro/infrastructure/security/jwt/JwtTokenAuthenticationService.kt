package com.cocro.infrastructure.security.jwt

import com.cocro.application.auth.port.TokenAuthenticationService
import com.cocro.kernel.auth.enum.Role
import com.cocro.kernel.auth.model.AuthenticatedUser
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.stereotype.Component

@Component
class JwtTokenAuthenticationService(
    private val jwtDecoder: JwtDecoder,
) : TokenAuthenticationService {
    override fun authenticate(token: String): AuthenticatedUser? =
        try {
            val jwt = jwtDecoder.decode(token)

            val roles =
                jwt
                    .getClaimAsStringList("roles")
                    ?.map { Role.valueOf(it) }
                    ?.toSet()
                    ?: emptySet()

            AuthenticatedUser(
                userId = jwt.subject,
                roles = roles,
            )
        } catch (e: Exception) {
            null
        }
}
