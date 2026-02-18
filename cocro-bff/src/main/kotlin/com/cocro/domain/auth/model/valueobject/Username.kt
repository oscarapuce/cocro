package com.cocro.domain.auth.model.valueobject

import com.cocro.kernel.auth.rule.UsernameRule

@JvmInline
value class Username(
    val value: String,
) {
    init {
        require(UsernameRule.validate(value)) {
            "Username must be not blank between ${UsernameRule.USERNAME_MIN_LENGTH} " +
                "and ${UsernameRule.USERNAME_MAX_LENGTH} characters and must contain only alphanumeric " +
                "characters or underscores"
        }
    }

    override fun toString(): String = value
}
