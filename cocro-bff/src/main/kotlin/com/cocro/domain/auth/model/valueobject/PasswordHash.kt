package com.cocro.domain.auth.model.valueobject

@JvmInline
value class PasswordHash(
    val value: String,
) {
    override fun toString(): String = "****"
}
