package com.cocro.kernel.auth.rule

import com.cocro.kernel.common.rule.CocroRule

object EmailRule : CocroRule<String> {
    const val EMAIL_REGEX = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$"

    override val arity = 1

    override fun isValid(values: List<String>): Boolean {
        val email = values.first()
        return Regex(EMAIL_REGEX).matches(email)
    }
}
