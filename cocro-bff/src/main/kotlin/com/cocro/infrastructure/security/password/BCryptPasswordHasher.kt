package com.cocro.infrastructure.security.password

import com.cocro.application.auth.port.PasswordHasher
import com.cocro.domain.auth.valueobject.PasswordHash
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Component

@Component
class BCryptPasswordHasher : PasswordHasher {
    private val encoder = BCryptPasswordEncoder()

    override fun hash(password: String): PasswordHash = PasswordHash(encoder.encode(password))

    override fun matches(
        password: String,
        passwordHash: PasswordHash,
    ): Boolean = encoder.matches(password, passwordHash.value)
}
