package com.cocro.application.auth.usecase

import com.cocro.application.auth.dto.AuthSuccess
import com.cocro.application.auth.dto.LoginUserCommandDto
import com.cocro.application.auth.mapper.toAuthSuccess
import com.cocro.application.auth.port.PasswordHasher
import com.cocro.application.auth.port.TokenIssuer
import com.cocro.application.auth.port.UserRepository
import com.cocro.domain.auth.error.AuthError
import com.cocro.domain.auth.model.valueobject.Username
import com.cocro.domain.auth.rule.UsernameRule
import com.cocro.domain.common.CocroResult
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class LoginUserUseCase(
    private val userRepository: UserRepository,
    private val passwordHasher: PasswordHasher,
    private val tokenIssuer: TokenIssuer,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun execute(command: LoginUserCommandDto): CocroResult<AuthSuccess, AuthError> {
        // VALIDATION
        if (!UsernameRule.validate(command.username)) {
            logger.warn("Login rejected: invalid username format")
            return CocroResult.Error(listOf(AuthError.UsernameInvalid))
        }

        // MAPPING
        val username = Username(command.username)

        // CHECK
        val user =
            userRepository.findByUsername(username)
                ?: run {
                    logger.warn("Login rejected: user {} not found", command.username)
                    return CocroResult.Error(listOf(AuthError.InvalidCredentials))
                }

        val matches =
            passwordHasher.matches(
                command.password,
                user.passwordHash,
            )

        if (!matches) {
            logger.warn("Login rejected: invalid password for user {}", command.username)
            return CocroResult.Error(listOf(AuthError.InvalidCredentials))
        }

        // SUCCESS
        val token =
            tokenIssuer.issue(
                userId = user.id,
                username = user.username.value,
                roles = user.roles,
            )

        logger.info("User {} successfully logged in", user.username.value)
        return CocroResult.Success(user.toAuthSuccess(token))
    }
}
