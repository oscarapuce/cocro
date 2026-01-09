package com.cocro.infrastructure.security.jwt

import com.nimbusds.jose.jwk.source.ImmutableSecret
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import java.util.Base64
import javax.crypto.spec.SecretKeySpec

@Configuration
@EnableConfigurationProperties(JwtProperties::class)
class JwtCryptoConfig(
    private val props: JwtProperties,
) {
    @Bean
    fun jwtEncoder(): JwtEncoder {
        val key =
            SecretKeySpec(
                Base64.getDecoder().decode(props.secret),
                "HmacSHA256",
            )
        return NimbusJwtEncoder(ImmutableSecret(key))
    }

    @Bean
    fun jwtDecoder(): JwtDecoder {
        val key =
            SecretKeySpec(
                Base64.getDecoder().decode(props.secret),
                "HmacSHA256",
            )
        return NimbusJwtDecoder.withSecretKey(key).build()
    }
}
