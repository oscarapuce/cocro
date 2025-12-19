package com.cocro.presentation.common.error

import com.cocro.kernel.common.error.CocroError

fun CocroError.toApiError(): ApiError {
    val errorCode = this.errorCode

    return ApiError(
        code = errorCode.name,
        message = errorCode.message,
        context = this.context(),
    )
}
