package com.cocro.application.auth.usecase

import com.cocro.application.auth.port.TokenIssuer
import com.cocro.infrastructure.util.SpiceNameGenerator
import com.cocro.domain.auth.enum.Role
import com.cocro.domain.auth.model.valueobject.UserId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test



class GuestLoginUseCaseTest {

    // Stubs manuels — Mockito ne supporte pas @JvmInline value class (UserId)
    private val tokenIssuer = object : TokenIssuer {
        override fun issue(userId: UserId, roles: Set<Role>) = "guest-jwt-token"
    }
    private val spiceNameGenerator = object : SpiceNameGenerator() {
        override fun generate() = "Cardamome-4821"
    }

    private val useCase = GuestLoginUseCase(tokenIssuer, spiceNameGenerator)

    @Test
    fun `should create guest with ANONYMOUS role and spice name`() {
        // when
        val result = useCase.execute()

        // then
        assertThat(result.username).isEqualTo("Cardamome-4821")
        assertThat(result.roles).containsExactly("ANONYMOUS")
        assertThat(result.token).isEqualTo("guest-jwt-token")
        assertThat(result.userId).isNotBlank()
    }

    @Test
    fun `should generate a different userId each call`() {
        // when
        val result1 = useCase.execute()
        val result2 = useCase.execute()

        // then
        assertThat(result1.userId).isNotEqualTo(result2.userId)
    }

    @Test
    fun `SpiceNameGenerator should produce name matching Spice-NNNN pattern`() {
        val generator = SpiceNameGenerator()

        repeat(20) {
            val name = generator.generate()
            val parts = name.split("-")
            assertThat(parts).hasSize(2)
            assertThat(parts[0]).isNotBlank()
            assertThat(parts[1]).hasSize(4).matches("\\d{4}")
        }
    }
}
