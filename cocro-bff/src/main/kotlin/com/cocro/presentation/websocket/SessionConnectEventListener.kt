package com.cocro.presentation.websocket

import com.cocro.application.session.dto.notification.SessionEvent
import com.cocro.application.session.port.SessionGridStateCache
import com.cocro.application.session.port.SessionNotifier
import com.cocro.application.session.port.SessionRepository
import com.cocro.kernel.session.model.valueobject.SessionShareCode
import com.cocro.kernel.session.rule.ParticipantsRule
import com.cocro.kernel.session.rule.SessionShareCodeRule
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationListener
import org.springframework.messaging.simp.stomp.StompHeaderAccessor
import org.springframework.stereotype.Component
import org.springframework.web.socket.messaging.SessionConnectedEvent

/**
 * Listens for STOMP CONNECT events.
 *
 * If the client passed a "shareCode" header on CONNECT, the server sends back
 * a private [SessionEvent.SessionWelcome] on /user/queue/session telling the client:
 *  - which topic to SUBSCRIBE to: /topic/session/{shareCode}
 *  - the current participant count and session status
 *  - the current grid state revision (so the client knows if it's up to date)
 *
 * Client-side flow:
 *   1. CONNECT  ws://host/ws  { Authorization: Bearer <token>, shareCode: XXXX }
 *   2. SUBSCRIBE /user/queue/session   ← listen for the welcome
 *   3. Receive SessionWelcome { topicToSubscribe: "/topic/session/XXXX", ... }
 *   4. SUBSCRIBE /topic/session/XXXX  ← now receiving all session broadcasts
 */
@Component
class SessionConnectEventListener(
    private val sessionRepository: SessionRepository,
    private val sessionGridStateCache: SessionGridStateCache,
    private val sessionNotifier: SessionNotifier,
) : ApplicationListener<SessionConnectedEvent> {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun onApplicationEvent(event: SessionConnectedEvent) {
        val accessor = StompHeaderAccessor.wrap(event.message)
        val principal = accessor.user as? CocroPrincipal ?: return

        val shareCode = accessor.getFirstNativeHeader("shareCode")
            ?.takeIf { SessionShareCodeRule.validate(it) }
            ?: return // no shareCode header → not joining a session tunnel, ignore

        logger.info(
            "WS connected: user={} for session shareCode={}",
            principal.userId(),
            shareCode,
        )

        val sessionShareCode = SessionShareCode(shareCode)

        // Check cache first (hot session), fall back to repo (cold/just-created session)
        val session = sessionRepository.findByShareCode(sessionShareCode)
        if (session == null) {
            logger.warn(
                "WS connected: session not found for shareCode={}, user={}",
                shareCode,
                principal.userId(),
            )
            return
        }

        val gridRevision = sessionGridStateCache.get(session.id)?.revision?.value
            ?: session.sessionGridState.revision.value

        val participantCount = ParticipantsRule.countActiveParticipants(session.participants)
        val topic = "/topic/session/$shareCode"

        sessionNotifier.notifyUser(
            principal.userId(),
            SessionEvent.SessionWelcome(
                shareCode = shareCode,
                topicToSubscribe = topic,
                participantCount = participantCount,
                status = session.status.name,
                gridRevision = gridRevision,
            ),
        )

        logger.debug(
            "SessionWelcome sent to user={} for session={} ({} participants, status={}, gridRevision={})",
            principal.userId(),
            shareCode,
            participantCount,
            session.status,
            gridRevision,
        )
    }
}

