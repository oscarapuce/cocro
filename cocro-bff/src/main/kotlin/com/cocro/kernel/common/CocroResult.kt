package com.cocro.kernel.common

import com.cocro.kernel.common.error.CocroError

sealed class CocroResult<out T, out E : CocroError> {
    data class Success<T>(
        val value: T,
    ) : CocroResult<T, Nothing>()

    data class Error<E : CocroError>(
        val errors: List<E>,
    ) : CocroResult<Nothing, E>()
}
