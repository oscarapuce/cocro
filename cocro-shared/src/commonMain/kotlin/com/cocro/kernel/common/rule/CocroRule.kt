package com.cocro.kernel.common.rule

interface CocroRule<T> {
    val arity: Int

    fun isValid(values: List<T>): Boolean

    fun validate(vararg values: T): Boolean {
        require(values.size == arity) {
            "Rule expects $arity values, got ${values.size}"
        }
        return isValid(values.toList())
    }
}
