package com.cocro.application.auth.usecase

import com.cocro.application.auth.dto.LoginUserCommandDto
import com.cocro.application.auth.port.PasswordHasher
import com.cocro.application.auth.port.TokenIssuer
import com.cocro.application.auth.port.UserRepository
import com.cocro.domain.auth.model.valueobject.Username
import com.cocro.kernel.auth.error.AuthError
import com.cocro.kernel.auth.model.AuthSuccess
import com.cocro.kernel.auth.rule.UsernameRule
import com.cocro.kernel.common.CocroResult
import org.springframework.stereotype.Service

@Service
class LoginUserUseCase(
    private val userRepository: UserRepository,
    private val passwordHasher: PasswordHasher,
    private val tokenIssuer: TokenIssuer,
) {
    fun execute(command: LoginUserCommandDto): CocroResult<AuthSuccess, AuthError> {
        if (!UsernameRule.validate(command.username)) {
            return CocroResult.Error(listOf(AuthError.UsernameInvalid))
        }

        val username = Username(command.username)

        val user =
            userRepository.findByUsername(username)
                ?: return CocroResult.Error(listOf(AuthError.InvalidCredentials))

        val matches =
            passwordHasher.matches(
                command.password,
                user.passwordHash,
            )

        if (!matches) {
            return CocroResult.Error(listOf(AuthError.InvalidCredentials))
        }

        val token =
            tokenIssuer.issue(
                userId = user.id,
                roles = user.roles,
            )

        return CocroResult.Success(
            AuthSuccess(
                userId = user.id.value.toString(),
                username = user.username.value,
                roles = user.roles,
                token = token,
            ),
        )
    }
}
