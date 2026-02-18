package com.cocro.application.auth.port

import com.cocro.domain.auth.model.valueobject.PasswordHash

interface PasswordHasher {
    fun hash(password: String): PasswordHash

    fun matches(
        password: String,
        passwordHash: PasswordHash,
    ): Boolean
}
