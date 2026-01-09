package com.cocro.application.auth.validation

import com.cocro.application.auth.dto.RegisterUserCommand
import com.cocro.kernel.auth.error.AuthError
import com.cocro.kernel.auth.rule.EmailRule
import com.cocro.kernel.auth.rule.PasswordRule
import com.cocro.kernel.auth.rule.UsernameRule

internal fun validateRegisterCommand(dto: RegisterUserCommand): List<AuthError> {
    var errors = emptyList<AuthError>()
    if (!UsernameRule.validate(dto.username)) {
        errors = errors + AuthError.UsernameInvalid
    }
    if (!dto.email.isNullOrBlank() && !EmailRule.validate(dto.email)) {
        errors = errors + AuthError.EmailInvalid
    }
    if (!PasswordRule.isValid(dto.password)) {
        errors = errors + AuthError.PasswordInvalid
    }
    return errors
}
