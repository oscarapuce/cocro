package com.cocro.presentation.websocket

import com.cocro.application.auth.port.TokenAuthenticationService
import org.springframework.messaging.Message
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.MessagingException
import org.springframework.messaging.simp.stomp.StompCommand
import org.springframework.messaging.simp.stomp.StompHeaderAccessor
import org.springframework.messaging.support.ChannelInterceptor
import org.springframework.stereotype.Component

@Component
class StompAuthChannelInterceptor(
    private val tokenAuthService: TokenAuthenticationService,
) : ChannelInterceptor {
    override fun preSend(
        message: Message<*>,
        channel: MessageChannel,
    ): Message<*>? {
        val accessor = StompHeaderAccessor.wrap(message)

        if (accessor.command == StompCommand.CONNECT) {
            val authHeader =
                accessor.getFirstNativeHeader("Authorization")
                    ?: throw MessagingException("Missing Authorization header")

            if (!authHeader.startsWith("Bearer ")) {
                throw MessagingException("Invalid Authorization header")
            }

            val token = authHeader.removePrefix("Bearer ").trim()

            val authenticatedUser =
                tokenAuthService.authenticate(token)
                    ?: throw MessagingException("Invalid or expired token")

            accessor.user = CocroPrincipal(authenticatedUser)
        }

        return message
    }
}
