package com.cocro.infrastructure.security.jwt

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "cocro.jwt")
data class JwtProperties(
    val issuer: String,
    val secret: String,
    val expirationSeconds: Long,
)
