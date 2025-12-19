package com.cocro.kernel.common.rule

object SafeStringRule : CocroRule<String> {

    const val SAFE_STRING_MAX_LENGTH = 200
    const val SAFE_STRING_BLACKLISTED_CHARS = "<>"

    override val arity = 1

    override fun isValid(values: List<String>): Boolean {
        val value = values.first()
        return value.isBlank() ||
                (value.length <= SAFE_STRING_MAX_LENGTH
                && value.none { it in SAFE_STRING_BLACKLISTED_CHARS })
    }
}