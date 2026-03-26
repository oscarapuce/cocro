package com.cocro.kernel.session.model.valueobject

import com.cocro.kernel.session.rule.SessionShareCodeRule

@JvmInline
value class SessionShareCode(
    val value: String,
) {
    init {
        require(SessionShareCodeRule.validate(value)) {
            "Session ID must be a 4-character alphanumeric string (A-Z, 0-9)"
        }
    }

    override fun toString(): String = value
}
