package com.cocro.presentation.websocket

import com.cocro.application.session.port.HeartbeatTracker
import com.cocro.application.session.port.SessionRepository
import com.cocro.domain.session.model.valueobject.SessionShareCode
import com.cocro.infrastructure.security.spring.CocroAuthentication
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.messaging.simp.stomp.StompHeaderAccessor
import org.springframework.stereotype.Component
import org.springframework.web.socket.messaging.SessionDisconnectEvent

/**
 * Listens for STOMP WebSocket disconnect events.
 * When a user disconnects, they are marked as "away" — a 30s grace period starts.
 * If they reconnect (via POST /join) within the grace period, the disconnect is transparent.
 * Otherwise, [HeartbeatTimeoutScheduler] will confirm them as timed out.
 *
 * The session's shareCode is resolved from WebSocket session attributes (set during STOMP CONNECT
 * by [StompAuthChannelInterceptor]), not from a Redis reverse lookup — this correctly supports
 * users connected to multiple sessions simultaneously.
 */
@Component
class StompSessionEventListener(
    private val heartbeatTracker: HeartbeatTracker,
    private val sessionRepository: SessionRepository,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @EventListener
    fun onDisconnect(event: SessionDisconnectEvent) {
        val principal = event.user as? CocroAuthentication ?: run {
            logger.debug("STOMP disconnect without CocroAuthentication — skipping heartbeat update")
            return
        }

        val userId = principal.user.userId

        val accessor = StompHeaderAccessor.wrap(event.message)
        val shareCode = accessor.sessionAttributes
            ?.get(StompAuthChannelInterceptor.SESSION_SHARE_CODE_KEY) as? String
            ?: run {
                logger.debug("User {} disconnected but no shareCode in session attributes", userId)
                return
            }

        val session = sessionRepository.findByShareCode(SessionShareCode(shareCode)) ?: run {
            logger.debug("User {} disconnected from unknown session shareCode={}", userId, shareCode)
            return
        }

        heartbeatTracker.markAway(session.id, userId)
        logger.info("User {} went away in session {} (grace period started)", userId, session.id.value)
    }
}
