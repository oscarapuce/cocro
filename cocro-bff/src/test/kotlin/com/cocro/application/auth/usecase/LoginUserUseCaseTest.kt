package com.cocro.application.auth.usecase

import com.cocro.application.auth.dto.LoginUserCommandDto
import com.cocro.application.auth.port.PasswordHasher
import com.cocro.application.auth.port.TokenIssuer
import com.cocro.application.auth.port.UserRepository
import com.cocro.domain.auth.enum.Role
import com.cocro.domain.auth.error.AuthError
import com.cocro.domain.auth.model.User
import com.cocro.domain.auth.model.valueobject.PasswordHash
import com.cocro.domain.auth.model.valueobject.UserId
import com.cocro.domain.auth.model.valueobject.Username
import com.cocro.domain.common.CocroResult
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class LoginUserUseCaseTest {

    private val userRepository: UserRepository = mock()
    private val passwordHasher: PasswordHasher = mock()
    private val tokenIssuer: TokenIssuer = mock()

    private val useCase = LoginUserUseCase(userRepository, passwordHasher, tokenIssuer)

    private val existingUser = User.reconstitute(
        id = UserId.new(),
        username = Username("validUser"),
        passwordHash = PasswordHash("hashed"),
        roles = setOf(Role.PLAYER),
        email = null,
    )

    @Test
    fun `should login successfully and return auth token`() {
        // given
        val dto = LoginUserCommandDto(username = "validUser", password = "Secure1!")
        whenever(userRepository.findByUsername(Username("validUser"))).thenReturn(existingUser)
        whenever(passwordHasher.matches("Secure1!", existingUser.passwordHash)).thenReturn(true)
        whenever(tokenIssuer.issue(existingUser.id, existingUser.roles)).thenReturn("jwt-token")

        // when
        val result = useCase.execute(dto)

        // then
        assertThat(result).isInstanceOf(CocroResult.Success::class.java)
        val success = (result as CocroResult.Success).value
        assertThat(success.token).isEqualTo("jwt-token")
        assertThat(success.username).isEqualTo("validUser")
    }

    @Test
    fun `should return error when username format is invalid`() {
        // given
        val dto = LoginUserCommandDto(username = "ab", password = "Secure1!")

        // when
        val result = useCase.execute(dto)

        // then
        assertThat(result).isInstanceOf(CocroResult.Error::class.java)
        val errors = (result as CocroResult.Error).errors
        assertThat(errors).contains(AuthError.UsernameInvalid)
        verifyNoInteractions(userRepository)
    }

    @Test
    fun `should return error when user is not found`() {
        // given
        val dto = LoginUserCommandDto(username = "unknownUser", password = "Secure1!")
        whenever(userRepository.findByUsername(Username("unknownUser"))).thenReturn(null)

        // when
        val result = useCase.execute(dto)

        // then
        assertThat(result).isInstanceOf(CocroResult.Error::class.java)
        val errors = (result as CocroResult.Error).errors
        assertThat(errors).contains(AuthError.InvalidCredentials)
        verifyNoInteractions(passwordHasher)
    }

    @Test
    fun `should return error when password does not match`() {
        // given
        val dto = LoginUserCommandDto(username = "validUser", password = "WrongPass1!")
        whenever(userRepository.findByUsername(Username("validUser"))).thenReturn(existingUser)
        whenever(passwordHasher.matches("WrongPass1!", existingUser.passwordHash)).thenReturn(false)

        // when
        val result = useCase.execute(dto)

        // then
        assertThat(result).isInstanceOf(CocroResult.Error::class.java)
        val errors = (result as CocroResult.Error).errors
        assertThat(errors).contains(AuthError.InvalidCredentials)
        verifyNoInteractions(tokenIssuer)
    }
}
