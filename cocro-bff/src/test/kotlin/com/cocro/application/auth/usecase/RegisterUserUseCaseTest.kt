package com.cocro.application.auth.usecase

import com.cocro.application.auth.dto.RegisterUserCommandDto
import com.cocro.application.auth.port.PasswordHasher
import com.cocro.application.auth.port.TokenIssuer
import com.cocro.application.auth.port.UserRepository
import com.cocro.kernel.auth.enum.Role
import com.cocro.kernel.auth.error.AuthError
import com.cocro.kernel.auth.model.User
import com.cocro.kernel.auth.model.valueobject.PasswordHash
import com.cocro.kernel.auth.model.valueobject.UserId
import com.cocro.kernel.auth.model.valueobject.Username
import com.cocro.kernel.common.CocroResult
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class RegisterUserUseCaseTest {

    private val userRepository: UserRepository = mock()
    private val passwordHasher: PasswordHasher = mock()
    private val tokenIssuer: TokenIssuer = mock()

    private val useCase = RegisterUserUseCase(userRepository, passwordHasher, tokenIssuer)

    @Test
    fun `should register user successfully and return auth token`() {
        // given
        val dto = RegisterUserCommandDto(username = "validUser", password = "Secure1!", email = null)
        val hash = PasswordHash("hashed")
        val savedUser = User.reconstitute(
            id = UserId.new(),
            username = Username("validUser"),
            passwordHash = hash,
            roles = setOf(Role.PLAYER),
            email = null,
        )
        whenever(userRepository.findByUsername(Username("validUser"))).thenReturn(null)
        whenever(passwordHasher.hash("Secure1!")).thenReturn(hash)
        whenever(userRepository.save(any())).thenReturn(savedUser)
        whenever(tokenIssuer.issue(savedUser.id, savedUser.roles)).thenReturn("jwt-token")

        // when
        val result = useCase.execute(dto)

        // then
        assertThat(result).isInstanceOf(CocroResult.Success::class.java)
        val success = (result as CocroResult.Success).value
        assertThat(success.token).isEqualTo("jwt-token")
        assertThat(success.username).isEqualTo("validUser")
    }

    @Test
    fun `should return error when username already exists`() {
        // given
        val dto = RegisterUserCommandDto(username = "takenUser", password = "Secure1!", email = null)
        val existingUser = User.reconstitute(
            id = UserId.new(),
            username = Username("takenUser"),
            passwordHash = PasswordHash("hash"),
            roles = setOf(Role.PLAYER),
            email = null,
        )
        whenever(userRepository.findByUsername(Username("takenUser"))).thenReturn(existingUser)

        // when
        val result = useCase.execute(dto)

        // then
        assertThat(result).isInstanceOf(CocroResult.Error::class.java)
        val errors = (result as CocroResult.Error).errors
        assertThat(errors).anyMatch { it is AuthError.UsernameAlreadyExists }
        verify(userRepository, never()).save(any())
    }

    @Test
    fun `should return error when username is invalid`() {
        // given
        val dto = RegisterUserCommandDto(username = "ab", password = "Secure1!", email = null)

        // when
        val result = useCase.execute(dto)

        // then
        assertThat(result).isInstanceOf(CocroResult.Error::class.java)
        val errors = (result as CocroResult.Error).errors
        assertThat(errors).contains(AuthError.UsernameInvalid)
        verifyNoInteractions(userRepository)
    }

    @Test
    fun `should return error when password is too weak`() {
        // given
        val dto = RegisterUserCommandDto(username = "validUser", password = "weak", email = null)

        // when
        val result = useCase.execute(dto)

        // then
        assertThat(result).isInstanceOf(CocroResult.Error::class.java)
        val errors = (result as CocroResult.Error).errors
        assertThat(errors).contains(AuthError.PasswordInvalid)
        verifyNoInteractions(userRepository)
    }

    @Test
    fun `should return error when email format is invalid`() {
        // given
        val dto = RegisterUserCommandDto(username = "validUser", password = "Secure1!", email = "not-an-email")

        // when
        val result = useCase.execute(dto)

        // then
        assertThat(result).isInstanceOf(CocroResult.Error::class.java)
        val errors = (result as CocroResult.Error).errors
        assertThat(errors).contains(AuthError.EmailInvalid)
        verifyNoInteractions(userRepository)
    }

    @Test
    fun `should succeed with valid email`() {
        // given
        val dto = RegisterUserCommandDto(username = "validUser", password = "Secure1!", email = "user@example.com")
        val hash = PasswordHash("hashed")
        val savedUser = User.reconstitute(
            id = UserId.new(),
            username = Username("validUser"),
            passwordHash = hash,
            roles = setOf(Role.PLAYER),
            email = null,
        )
        whenever(userRepository.findByUsername(Username("validUser"))).thenReturn(null)
        whenever(passwordHasher.hash(any())).thenReturn(hash)
        whenever(userRepository.save(any())).thenReturn(savedUser)
        whenever(tokenIssuer.issue(savedUser.id, savedUser.roles)).thenReturn("jwt-token")

        // when
        val result = useCase.execute(dto)

        // then
        assertThat(result).isInstanceOf(CocroResult.Success::class.java)
    }
}
