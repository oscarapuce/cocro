package com.cocro.domain.common.rule

abstract class CocroRule<T> {
    abstract val arity: Int

    protected abstract fun isValid(values: List<T>): Boolean

    fun validate(vararg values: T): Boolean {
        val isSizeNonRestricted = arity < 0
        require(isSizeNonRestricted || values.size == arity) {
            "Rule expects $arity values, got ${values.size}"
        }
        return isValid(values.toList())
    }
}
