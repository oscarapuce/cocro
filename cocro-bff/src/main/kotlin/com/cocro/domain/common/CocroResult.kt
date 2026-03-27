package com.cocro.domain.common

import com.cocro.domain.common.error.CocroError

sealed class CocroResult<out T, out E : CocroError> {
    data class Success<T>(
        val value: T,
    ) : CocroResult<T, Nothing>()

    data class Error<E : CocroError>(
        val errors: List<E>,
    ) : CocroResult<Nothing, E>()
}
