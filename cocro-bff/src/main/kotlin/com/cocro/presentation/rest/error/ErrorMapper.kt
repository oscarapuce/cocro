package com.cocro.presentation.rest.error

import com.cocro.kernel.common.Result
import com.cocro.kernel.common.error.CocroError
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity

fun CocroError.toApiError(): ApiError {
    val errorCode = this.errorCode

    return ApiError(
        code = errorCode.name,
        message = errorCode.message,
        context = this.context(),
    )
}

fun Result.Error<out CocroError>.toErrorResponse(): ResponseEntity<Map<String, List<ApiError>>> {
    val apiErrors = errors.map { it.toApiError() }
    val status = errors.maxOf { it.errorCode.httpCode }

    return ResponseEntity
        .status(status)
        .body(
            mapOf(
                "errors" to apiErrors,
            ),
        )
}

fun <T> Result<T, CocroError>.toResponseEntity(successStatus: HttpStatus = HttpStatus.OK): ResponseEntity<*> =
    when (this) {
        is Result.Success ->
            ResponseEntity
                .status(successStatus)
                .body(value)

        is Result.Error ->
            toErrorResponse()
    }
