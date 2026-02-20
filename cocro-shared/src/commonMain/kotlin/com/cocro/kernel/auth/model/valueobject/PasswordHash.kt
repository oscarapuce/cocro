package com.cocro.kernel.auth.model.valueobject

@JvmInline
value class PasswordHash(
    val value: String,
) {
    override fun toString(): String = "****"
}
