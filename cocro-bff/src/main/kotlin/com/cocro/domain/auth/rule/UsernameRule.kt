package com.cocro.domain.auth.rule

import com.cocro.domain.common.rule.CocroRule

object UsernameRule : CocroRule<String>() {
    const val USERNAME_MAX_LENGTH = 20
    const val USERNAME_MIN_LENGTH = 3
    const val USERNAME_REGEX = "^[a-zA-Z0-9_]+$"

    override val arity: Int = 1

    override fun isValid(values: List<String>): Boolean {
        val username = values.first()
        return username.isNotBlank() &&
            username.length in USERNAME_MIN_LENGTH..USERNAME_MAX_LENGTH &&
            Regex(USERNAME_REGEX).matches(username)
    }
}
