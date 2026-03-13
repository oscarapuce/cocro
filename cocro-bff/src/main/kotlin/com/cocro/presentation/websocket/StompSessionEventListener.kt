package com.cocro.presentation.websocket

import com.cocro.application.session.port.HeartbeatTracker
import com.cocro.infrastructure.security.spring.CocroAuthentication
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.web.socket.messaging.SessionDisconnectEvent

/**
 * Listens for STOMP WebSocket disconnect events.
 * When a user disconnects, they are marked as "away" — a 30s grace period starts.
 * If they reconnect (via POST /join) within the grace period, the disconnect is transparent.
 * Otherwise, [HeartbeatTimeoutScheduler] will confirm them as timed out.
 */
@Component
class StompSessionEventListener(
    private val heartbeatTracker: HeartbeatTracker,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @EventListener
    fun onDisconnect(event: SessionDisconnectEvent) {
        val principal = event.user as? CocroAuthentication ?: run {
            logger.debug("STOMP disconnect without CocroAuthentication — skipping heartbeat update")
            return
        }

        val userId = principal.user.userId
        val sessionId = heartbeatTracker.getSessionIdForUser(userId) ?: run {
            logger.debug("User {} disconnected but was not tracked in any session", userId)
            return
        }

        heartbeatTracker.markAway(sessionId, userId)
        logger.info("User {} went away in session {} (grace period started)", userId, sessionId.value)
    }
}
