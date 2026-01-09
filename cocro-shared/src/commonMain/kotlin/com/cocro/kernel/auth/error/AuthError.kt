package com.cocro.kernel.auth.error

import com.cocro.kernel.common.error.CocroError
import com.cocro.kernel.common.error.ErrorCode

sealed interface AuthError : CocroError {
    data class UsernameAlreadyExists(
        val username: String,
    ) : AuthError {
        override val errorCode = ErrorCode.AUTH_USERNAME_ALREADY_EXISTS

        override fun context(): Map<String, String> = mapOf("username" to username)
    }

    object InvalidCredentials : AuthError {
        override val errorCode = ErrorCode.AUTH_INVALID_CREDENTIALS
    }

    object UsernameInvalid : AuthError {
        override val errorCode = ErrorCode.AUTH_USERNAME_INVALID
    }

    object EmailInvalid : AuthError {
        override val errorCode = ErrorCode.AUTH_EMAIL_INVALID
    }

    object PasswordInvalid : AuthError {
        override val errorCode = ErrorCode.AUTH_PASSWORD_INVALID
    }

    object PasswordTooWeak : AuthError {
        override val errorCode = ErrorCode.AUTH_PASSWORD_TOO_WEAK
    }
}
