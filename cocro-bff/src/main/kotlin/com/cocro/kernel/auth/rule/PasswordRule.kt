package com.cocro.kernel.auth.rule

import com.cocro.kernel.common.rule.CocroRule

object PasswordRule : CocroRule<String>() {
    const val MIN_LENGTH = 5
    const val MAX_LENGTH = 64
    private const val PASSWORD_PATTERN =
        "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?]).+$"
    private val PASSWORD_REGEX = Regex(PASSWORD_PATTERN)

    override val arity = 1

    override fun isValid(values: List<String>): Boolean {
        val password = values.first()
        return password.length in MIN_LENGTH..MAX_LENGTH && PASSWORD_REGEX.matches(password)
    }
}
