package com.cocro.infrastructure.security.jwt

import org.springframework.core.convert.converter.Converter
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken

class JwtAuthenticationConverter : Converter<Jwt, AbstractAuthenticationToken> {
    override fun convert(jwt: Jwt): AbstractAuthenticationToken {
        val roles =
            jwt
                .getClaimAsStringList("roles")
                ?.map { SimpleGrantedAuthority("ROLE_$it") }
                ?: emptyList()

        return JwtAuthenticationToken(jwt, roles)
    }
}
