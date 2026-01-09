package com.cocro.infrastructure.security.jwt

import com.cocro.application.auth.port.TokenIssuer
import com.cocro.domain.auth.valueobject.UserId
import com.cocro.kernel.auth.enum.Role
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class JwtTokenIssuer(
    private val jwtEncoder: JwtEncoder,
    private val props: JwtProperties,
) : TokenIssuer {
    override fun issue(
        userId: UserId,
        roles: Set<Role>,
    ): String {
        val now = Instant.now()

        val claims =
            JwtClaimsSet
                .builder()
                .issuer(props.issuer)
                .subject(userId.value.toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(props.expirationSeconds))
                .claim("roles", roles.map { it.name })
                .build()

        return jwtEncoder.encode(JwtEncoderParameters.from(claims)).tokenValue
    }
}
