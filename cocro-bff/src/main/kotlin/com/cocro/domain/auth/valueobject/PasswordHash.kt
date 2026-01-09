package com.cocro.domain.auth.valueobject

@JvmInline
value class PasswordHash(
    val value: String,
) {
    override fun toString(): String = "****"
}
