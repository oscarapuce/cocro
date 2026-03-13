package com.cocro.presentation.websocket

import com.cocro.application.auth.port.TokenAuthenticationService
import com.cocro.infrastructure.security.spring.CocroAuthentication
import org.slf4j.LoggerFactory
import org.springframework.messaging.Message
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.MessageHandler
import org.springframework.messaging.MessagingException
import org.springframework.messaging.simp.SimpMessageHeaderAccessor
import org.springframework.messaging.simp.stomp.StompCommand
import org.springframework.messaging.simp.stomp.StompHeaderAccessor
import org.springframework.messaging.support.ExecutorChannelInterceptor
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component

/**
 * Handles STOMP-level authentication.
 *
 * - **CONNECT**: validates the JWT from the `Authorization` header and stores [CocroAuthentication]
 *   in WebSocket session attributes so it persists across frames.
 *
 * - **SEND/SUBSCRIBE (non-CONNECT)**: restores [CocroAuthentication] into [SecurityContextHolder]
 *   on the **executor thread** via [ExecutorChannelInterceptor.beforeHandle] (not in [preSend],
 *   which runs on the calling/Tomcat thread — a different thread-local than the handler thread).
 */
@Component
class StompAuthChannelInterceptor(
    private val tokenAuthService: TokenAuthenticationService,
) : ExecutorChannelInterceptor {
    private val logger = LoggerFactory.getLogger(javaClass)

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

            val auth = CocroAuthentication(authenticatedUser)
            accessor.user = auth
            accessor.sessionAttributes?.also { attrs ->
                // Store auth for SecurityContextHolder population on subsequent SEND/SUBSCRIBE frames
                // (accessor.user is null for those because session.getPrincipal() is not set
                // at the HTTP handshake level — we authenticate at the STOMP CONNECT level instead)
                attrs[SESSION_AUTH_KEY] = auth
                // Required for DefaultSimpUserRegistry: Spring reads session.getAttributes().get("simpUser")
                // in StompSubProtocolHandler.getUser(session) when firing SessionConnectedEvent,
                // which is how convertAndSendToUser() resolves the subscription destination.
                attrs[SimpMessageHeaderAccessor.USER_HEADER] = auth
                // Store the shareCode so SessionConnectedEvent listener can retrieve it
                // (the CONNECTED response frame doesn't carry the client's CONNECT native headers)
                accessor.getFirstNativeHeader("shareCode")
                    ?.let { attrs[SESSION_SHARE_CODE_KEY] = it }
            }
        }

        return message
    }

    /**
     * Runs on the **executor thread** — the same thread as the message handler.
     * Restores the WebSocket session's [CocroAuthentication] into [SecurityContextHolder]
     * so that use cases can call [CurrentUserProvider.currentUserOrNull] normally.
     */
    override fun beforeHandle(
        message: Message<*>,
        channel: MessageChannel,
        handler: MessageHandler,
    ): Message<*> {
        val accessor = StompHeaderAccessor.wrap(message)
        val auth = accessor.sessionAttributes?.get(SESSION_AUTH_KEY) as? CocroAuthentication
        if (auth != null) {
            SecurityContextHolder.getContext().authentication = auth
        }
        return message
    }

    override fun afterMessageHandled(
        message: Message<*>,
        channel: MessageChannel,
        handler: MessageHandler,
        ex: Exception?,
    ) {
        SecurityContextHolder.clearContext()
    }

    companion object {
        const val SESSION_AUTH_KEY = "COCRO_AUTH"
        const val SESSION_SHARE_CODE_KEY = "COCRO_SHARE_CODE"
    }
}
