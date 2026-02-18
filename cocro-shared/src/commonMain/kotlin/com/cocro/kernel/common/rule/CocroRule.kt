package com.cocro.kernel.common.rule

abstract class CocroRule<T> {
    abstract val arity: Int

    protected abstract fun isValid(values: List<T>): Boolean

    fun validate(vararg values: T): Boolean {
        require(values.size == arity) {
            "Rule expects $arity values, got ${values.size}"
        }
        return isValid(values.toList())
    }
}
