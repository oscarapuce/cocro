package com.cocro.application.auth.usecase

import com.cocro.application.auth.dto.RegisterUserCommandDto
import com.cocro.application.auth.port.PasswordHasher
import com.cocro.application.auth.port.TokenIssuer
import com.cocro.application.auth.port.UserRepository
import com.cocro.application.auth.validation.validateRegisterCommand
import com.cocro.domain.auth.model.User
import com.cocro.domain.auth.model.valueobject.Email
import com.cocro.domain.auth.model.valueobject.Username
import com.cocro.kernel.auth.error.AuthError
import com.cocro.kernel.auth.model.AuthSuccess
import com.cocro.kernel.common.CocroResult
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class RegisterUserUseCase(
    private val userRepository: UserRepository,
    private val passwordHasher: PasswordHasher,
    private val tokenIssuer: TokenIssuer,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun execute(command: RegisterUserCommandDto): CocroResult<AuthSuccess, AuthError> {
        val errors = validateRegisterCommand(command)

        if (errors.isNotEmpty()) {
            logger.warn("Register command rejected: {} errors", errors.size)
            return CocroResult.Error(errors)
        }

        val username = Username(command.username)

        if (userRepository.findByUsername(username) != null) {
            return CocroResult.Error(
                listOf(AuthError.UsernameAlreadyExists(command.username)),
            )
        }

        val passwordHash = passwordHasher.hash(command.password)

        val user =
            User.register(
                username = Username(command.username),
                passwordHash = passwordHash,
                email =
                    command.email?.let {
                        Email(
                            it,
                        )
                    },
            )

        val savedUser = userRepository.save(user)

        val token =
            tokenIssuer.issue(
                userId = savedUser.id,
                roles = savedUser.roles,
            )

        return CocroResult.Success(
            AuthSuccess(
                userId = savedUser.id.value.toString(),
                username = savedUser.username.value,
                roles = savedUser.roles,
                token = token,
            ),
        )
    }
}
