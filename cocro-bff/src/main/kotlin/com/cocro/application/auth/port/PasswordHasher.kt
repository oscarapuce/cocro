package com.cocro.application.auth.port

import com.cocro.kernel.auth.model.valueobject.PasswordHash

interface PasswordHasher {
    fun hash(password: String): PasswordHash

    fun matches(
        password: String,
        passwordHash: PasswordHash,
    ): Boolean
}
