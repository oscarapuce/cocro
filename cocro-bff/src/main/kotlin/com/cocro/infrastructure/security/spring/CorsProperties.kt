package com.cocro.infrastructure.security.spring

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "cocro.cors")
data class CorsProperties(
    val allowedOrigins: List<String>,
)
