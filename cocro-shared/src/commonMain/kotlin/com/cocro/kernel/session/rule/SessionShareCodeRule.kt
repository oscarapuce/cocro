package com.cocro.kernel.session.rule

import com.cocro.kernel.common.rule.CocroRule

object SessionShareCodeRule : CocroRule<String>() {
    const val SESSION_SHARE_CODE_SIZE = 4
    const val SESSION_SHARE_CODE_PATTERN = "[A-Z0-9]{$SESSION_SHARE_CODE_SIZE}"

    override val arity: Int = 1

    override fun isValid(values: List<String>): Boolean {
        val sessionShareCode = values.first()
        return SESSION_SHARE_CODE_PATTERN.toRegex().matches(sessionShareCode)
    }
}
