package com.cocro.kernel.auth.rule

object PasswordRule {
    const val MIN_LENGTH = 5
    const val MAX_LENGTH = 64

    val PASSWORD_REGEX =
        Regex("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[!@#\$%^&*()_+\\-=[\\]{};':\"\\\\|,.<>/?]).{$MIN_LENGTH,$MAX_LENGTH}\$")

    fun isValid(password: String): Boolean = PASSWORD_REGEX.matches(password)
}
