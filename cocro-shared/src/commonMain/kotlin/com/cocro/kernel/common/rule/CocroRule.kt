package com.cocro.kernel.common.rule

interface CocroRule<T> {
    fun validate(value: T): Boolean
}