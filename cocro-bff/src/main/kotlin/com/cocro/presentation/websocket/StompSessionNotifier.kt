package com.cocro.presentation.websocket

import com.cocro.application.session.dto.notification.SessionEvent
import com.cocro.application.session.port.SessionNotifier
import com.cocro.domain.auth.model.valueobject.UserId
import com.cocro.domain.session.model.valueobject.SessionShareCode
import org.slf4j.LoggerFactory
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Component

@Component
class StompSessionNotifier(
    private val messagingTemplate: SimpMessagingTemplate,
) : SessionNotifier {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun broadcast(shareCode: SessionShareCode, event: SessionEvent) {
        val destination = "/topic/session/${shareCode.value}"
        logger.debug("Broadcasting {} to {}", event::class.simpleName, destination)
        messagingTemplate.convertAndSend(destination, event)
    }

    override fun notifyUser(userId: UserId, event: SessionEvent) {
        logger.debug("Notifying user {} with {}", userId, event::class.simpleName)
        // Delivers to /user/{userId}/queue/session for the specific connected client
        messagingTemplate.convertAndSendToUser(userId.toString(), "/queue/session", event)
    }
}

