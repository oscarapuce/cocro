package com.cocro.kernel.common

import com.cocro.kernel.common.error.CocroError

sealed class Result<out T, out E : CocroError> {
    data class Success<T>(
        val value: T,
    ) : Result<T, Nothing>()

    data class Error<E : CocroError>(
        val errors: List<E>,
    ) : Result<Nothing, E>()
}
