package com.cocro.application.session.usecase

import com.cocro.application.auth.port.CurrentUserProvider
import com.cocro.application.session.dto.JoinSessionDto
import com.cocro.application.session.dto.SessionJoinSuccess
import com.cocro.application.session.dto.notification.SessionEvent
import com.cocro.application.session.mapper.toSessionJoinSuccess
import com.cocro.application.session.port.HeartbeatTracker
import com.cocro.application.session.port.SessionGridStateCache
import com.cocro.application.session.port.SessionNotifier
import com.cocro.application.session.port.SessionRepository
import com.cocro.application.session.validation.validateJoinSessionDto
import com.cocro.kernel.common.CocroResult
import com.cocro.kernel.session.enum.SessionStatus
import com.cocro.kernel.session.error.SessionError
import com.cocro.kernel.session.model.valueobject.SessionShareCode
import com.cocro.kernel.session.rule.ParticipantsRule
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class JoinSessionUseCase(
    private val currentUserProvider: CurrentUserProvider,
    private val sessionRepository: SessionRepository,
    private val sessionGridStateCache: SessionGridStateCache,
    private val sessionNotifier: SessionNotifier,
    private val heartbeatTracker: HeartbeatTracker,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun execute(joinSessionDto: JoinSessionDto): CocroResult<SessionJoinSuccess, SessionError> {
        // EARLY AUTH CHECK
        val user =
            currentUserProvider.currentUserOrNull()
                ?: run {
                    logger.warn("Session join rejected: user not authenticated")
                    return CocroResult.Error(listOf(SessionError.Unauthorized))
                }

        // VALIDATION
        val errors = validateJoinSessionDto(joinSessionDto)
        if (errors.isNotEmpty()) {
            logger.warn("Session join rejected: {} validation errors for shareCode={}", errors.size, joinSessionDto.shareCode)
            return CocroResult.Error(errors)
        }

        val sessionShareCode = SessionShareCode(joinSessionDto.shareCode)

        val session =
            sessionRepository.findByShareCode(sessionShareCode)
                ?: run {
                    logger.warn("Session join rejected: session not found with shareCode={}", sessionShareCode)
                    return CocroResult.Error(listOf(SessionError.SessionNotFound(sessionShareCode.toString())))
                }

        if (session.status !in setOf(SessionStatus.CREATING, SessionStatus.PLAYING)) {
            logger.warn(
                "Session join rejected: invalid status {} for session {} (expected CREATING or PLAYING)",
                session.status,
                session.shareCode.value,
            )
            return CocroResult.Error(listOf(SessionError.InvalidStatusForAction(session.status, "join")))
        }

        // TRANSPARENT RECONNECTION: user disconnected (STOMP) but is still within grace period
        if (heartbeatTracker.isAway(session.id, user.userId)) {
            heartbeatTracker.markActive(session.id, user.userId)
            val activeCount = ParticipantsRule.countActiveParticipants(session.participants)
            logger.info(
                "User {} transparently reconnected to session {} ({} participants)",
                user.userId(),
                session.shareCode.value,
                activeCount,
            )
            // No broadcast — reconnection is invisible to other participants
            return CocroResult.Success(session.toSessionJoinSuccess())
        }

        // STANDARD JOIN
        if (session.participants.any { it.userId == user.userId }) {
            logger.warn("Session join rejected: user {} already participant in session {}", user.userId(), session.shareCode.value)
            return CocroResult.Error(listOf(SessionError.AlreadyParticipant(user.userId(), session.shareCode.value)))
        }

        var activeParticipantCount = ParticipantsRule.countActiveParticipants(session.participants)
        if (!ParticipantsRule.canJoin(session.participants)) {
            logger.warn("Session join rejected: session {} is full ({} participants)", session.shareCode.value, activeParticipantCount)
            return CocroResult.Error(listOf(SessionError.SessionFull))
        }

        // PERSISTENCE
        val updatedSession = session.join(user.userId)
        val savedSession = sessionRepository.save(updatedSession)
        ++activeParticipantCount

        // Flush current grid state to Mongo alongside participant update
        sessionGridStateCache.get(session.id)?.let { gridState ->
            sessionRepository.updateGridState(session.id, gridState)
            sessionGridStateCache.markFlushed(session.id, gridState.revision.value)
        }

        // HEARTBEAT
        heartbeatTracker.markActive(session.id, user.userId)
        heartbeatTracker.registerUserSession(user.userId, session.id)

        // NOTIFICATION
        sessionNotifier.broadcast(
            savedSession.shareCode,
            SessionEvent.ParticipantJoined(
                userId = user.userId(),
                participantCount = activeParticipantCount,
            ),
        )

        logger.info(
            "User {} successfully joined session {} ({} participants)",
            user.userId(),
            savedSession.shareCode.value,
            activeParticipantCount,
        )
        return CocroResult.Success(savedSession.toSessionJoinSuccess())
    }
}
