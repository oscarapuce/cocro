package com.cocro.application.auth.usecase

import com.cocro.application.auth.dto.AuthSuccess
import com.cocro.application.auth.port.TokenIssuer
import com.cocro.infrastructure.util.SpiceNameGenerator
import com.cocro.domain.auth.enum.Role
import com.cocro.domain.auth.model.valueobject.UserId
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class GuestLoginUseCase(
    private val tokenIssuer: TokenIssuer,
    private val spiceNameGenerator: SpiceNameGenerator,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun execute(): AuthSuccess {
        val userId = UserId.new()
        val username = spiceNameGenerator.generate()
        val token = tokenIssuer.issue(userId, setOf(Role.ANONYMOUS))

        logger.info("Guest account created: userId={} username={}", userId.value, username)

        return AuthSuccess(
            userId = userId.value.toString(),
            username = username,
            roles = setOf(Role.ANONYMOUS.name),
            token = token,
        )
    }
}
