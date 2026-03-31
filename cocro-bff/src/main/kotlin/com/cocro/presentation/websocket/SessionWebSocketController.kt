package com.cocro.presentation.websocket

import com.cocro.application.session.dto.UpdateSessionGridDto
import com.cocro.application.session.dto.notification.SessionEvent
import com.cocro.application.session.port.HeartbeatTracker
import com.cocro.application.session.port.SessionGridStateCache
import com.cocro.application.session.port.SessionRepository
import com.cocro.application.session.usecase.UpdateSessionGridUseCases
import com.cocro.infrastructure.security.spring.CocroAuthentication
import com.cocro.domain.common.CocroResult
import com.cocro.domain.session.model.valueobject.SessionShareCode
import com.cocro.domain.session.rule.ParticipantsRule
import com.cocro.domain.session.rule.SessionShareCodeRule
import org.slf4j.LoggerFactory
import org.springframework.messaging.handler.annotation.DestinationVariable
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.messaging.simp.SimpMessageHeaderAccessor
import org.springframework.messaging.simp.annotation.SubscribeMapping
import org.springframework.stereotype.Controller

@Controller
class SessionWebSocketController(
    private val updateSessionGridUseCases: UpdateSessionGridUseCases,
    private val sessionRepository: SessionRepository,
    private val sessionGridStateCache: SessionGridStateCache,
    private val heartbeatTracker: HeartbeatTracker,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Client subscribes to: /app/session/{shareCode}/welcome
     * Server replies with [SessionEvent.SessionWelcome] directly (no timing race, no user-dest resolution).
     *
     * Client flow:
     *  1. CONNECT
     *  2. SUBSCRIBE /app/session/{shareCode}/welcome   ← receives SessionWelcome synchronously
     *  3. SUBSCRIBE /topic/session/{shareCode}         ← ongoing broadcasts
     */
    @SubscribeMapping("/session/{shareCode}/welcome")
    fun onWelcomeSubscribe(
        @DestinationVariable shareCode: String,
        headerAccessor: SimpMessageHeaderAccessor,
    ): SessionEvent.SessionWelcome? {
        val auth = headerAccessor.sessionAttributes
            ?.get(StompAuthChannelInterceptor.SESSION_AUTH_KEY) as? CocroAuthentication
            ?: run {
                logger.warn("onWelcomeSubscribe: unauthenticated subscribe for shareCode={}", shareCode)
                return null
            }
        if (!SessionShareCodeRule.validate(shareCode)) return null

        val session = sessionRepository.findByShareCode(SessionShareCode(shareCode)) ?: run {
            logger.warn("onWelcomeSubscribe: session not found shareCode={}", shareCode)
            return null
        }

        val gridRevision = sessionGridStateCache.get(session.id)?.revision?.value
            ?: session.sessionGridState.revision.value
        val participantCount = ParticipantsRule.countActiveParticipants(session.participants)

        logger.debug(
            "SessionWelcome → user={} shareCode={} participants={} status={}",
            auth.user.userId, shareCode, participantCount, session.status,
        )

        return SessionEvent.SessionWelcome(
            shareCode = shareCode,
            topicToSubscribe = "/topic/session/$shareCode",
            participantCount = participantCount,
            status = session.status.name,
            gridRevision = gridRevision,
        )
    }

    /**
     * Client sends to: /app/session/{shareCode}/grid
     * Server broadcasts result to: /topic/session/{shareCode}
     */
    @MessageMapping("/session/{shareCode}/grid")
    fun handleGridUpdate(
        @DestinationVariable shareCode: String,
        @Payload payload: GridUpdatePayload,
    ) {
        val dto = UpdateSessionGridDto(
            shareCode = shareCode,
            posX = payload.posX,
            posY = payload.posY,
            commandType = payload.commandType,
            letter = payload.letter,
        )


        when (val result = updateSessionGridUseCases.execute(dto)) {
            is CocroResult.Success -> logger.debug(
                "Grid update applied on session={}, pos=({},{}), command={}",
                shareCode, payload.posX, payload.posY, payload.commandType,
            )
            is CocroResult.Error -> logger.warn(
                "Grid update rejected on session={}: {}",
                shareCode, result.errors.map { it.errorCode },
            )
        }
    }

    /**
     * Client sends to: /app/session/{shareCode}/heartbeat
     * Keeps the user marked as ACTIVE in the HeartbeatTracker.
     * Should be sent every ≤20s by the client (grace period is 30s).
     */
    @MessageMapping("/session/{shareCode}/heartbeat")
    fun handleHeartbeat(
        @DestinationVariable shareCode: String,
        headerAccessor: SimpMessageHeaderAccessor,
    ) {
        val auth = headerAccessor.sessionAttributes
            ?.get(StompAuthChannelInterceptor.SESSION_AUTH_KEY) as? CocroAuthentication
            ?: return

        if (!SessionShareCodeRule.validate(shareCode)) return

        val session = sessionRepository.findByShareCode(SessionShareCode(shareCode)) ?: return

        heartbeatTracker.markActive(session.id, auth.user.userId)
        logger.trace("Heartbeat received from user={} for session={}", auth.user.userId, shareCode)
    }
}

data class GridUpdatePayload(
    val posX: Int,
    val posY: Int,
    val commandType: String,
    val letter: Char?,
)

