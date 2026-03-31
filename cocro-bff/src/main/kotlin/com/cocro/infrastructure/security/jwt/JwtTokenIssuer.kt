package com.cocro.infrastructure.security.jwt

import com.cocro.application.auth.port.TokenIssuer
import com.cocro.domain.auth.enum.Role
import com.cocro.domain.auth.model.valueobject.UserId
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
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
        username: String,
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
                .claim("username", username)
                .claim("roles", roles.map { it.name })
                .build()

        val header = JwsHeader.with(MacAlgorithm.HS256).build()
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).tokenValue
    }
}
