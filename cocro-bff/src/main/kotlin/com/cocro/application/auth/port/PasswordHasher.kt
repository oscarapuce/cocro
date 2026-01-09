package com.cocro.application.auth.port

import com.cocro.domain.auth.valueobject.PasswordHash

interface PasswordHasher {
    fun hash(password: String): PasswordHash

    fun matches(
        password: String,
        passwordHash: PasswordHash,
    ): Boolean
}
