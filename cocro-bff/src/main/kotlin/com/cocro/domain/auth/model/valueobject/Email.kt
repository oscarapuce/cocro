package com.cocro.domain.auth.model.valueobject

import com.cocro.domain.auth.rule.EmailRule

@JvmInline
value class Email(
    val value: String,
) {
    init {
        require(EmailRule.validate(value)) { "Invalid email address" }
    }

    override fun toString(): String = value
}
