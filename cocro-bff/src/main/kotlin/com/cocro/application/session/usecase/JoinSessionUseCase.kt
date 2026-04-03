package com.cocro.application.session.usecase

import com.cocro.application.auth.port.CurrentUserProvider
import com.cocro.application.session.dto.JoinSessionDto
import com.cocro.application.session.dto.SessionFullDto
import com.cocro.application.session.dto.notification.SessionEvent
import com.cocro.application.session.mapper.toSessionFullDto
import com.cocro.application.session.port.HeartbeatTracker
import com.cocro.application.session.port.SessionGridStateCache
import com.cocro.application.session.port.SessionNotifier
import com.cocro.application.session.port.SessionRepository
import com.cocro.application.session.validation.validateJoinSessionDto
import com.cocro.domain.common.CocroResult
import com.cocro.domain.session.error.SessionError
import com.cocro.domain.session.model.SessionLifecycleCommand
import com.cocro.domain.session.model.valueobject.SessionShareCode
import com.cocro.domain.session.enum.ParticipantStatus
import com.cocro.domain.session.enum.SessionStatus
import com.cocro.domain.session.rule.ParticipantsRule
import com.cocro.domain.session.rule.SessionLimitRule
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

    fun execute(joinSessionDto: JoinSessionDto): CocroResult<SessionFullDto, SessionError> {
        val user =
            currentUserProvider.currentUserOrNull()
                ?: run {
                    logger.warn("Session join rejected: user not authenticated")
                    return CocroResult.Error(listOf(SessionError.Unauthorized))
                }

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

        // TRANSPARENT RECONNECTION: app-layer concern (heartbeat is infrastructure)
        if (heartbeatTracker.isAway(session.id, user.userId)) {
            heartbeatTracker.markActive(session.id, user.userId)
            logger.info(
                "User {} transparently reconnected to session {} ({} participants)",
                user.userId(), session.shareCode.value,
                ParticipantsRule.countActiveParticipants(session.participants),
            )
            val gridState = sessionGridStateCache.get(session.id) ?: session.sessionGridState
            val activeCount = ParticipantsRule.countActiveParticipants(session.participants)
            return CocroResult.Success(session.toSessionFullDto(gridState, activeCount))
        }

        // SESSION LIMIT: max 5 active sessions per user (skip for idempotent rejoins handled below)
        val isAlreadyJoined = session.participants.any {
            it.userId == user.userId && it.status == ParticipantStatus.JOINED
        }
        if (!isAlreadyJoined) {
            val userActiveCount = sessionRepository.countActiveByUser(user.userId)
            if (!SessionLimitRule.canCreateOrJoin(userActiveCount)) {
                logger.warn("Session join rejected: user {} has {} active sessions (max {})", user.userId(), userActiveCount, SessionLimitRule.MAX_ACTIVE_SESSIONS)
                return CocroResult.Error(listOf(SessionError.ActiveSessionLimitReached))
            }
        }

        // DOMAIN COMMAND (validates status, capacity, duplicates)
        val updatedSession =
            when (val result = session.apply(SessionLifecycleCommand.Join(user.userId, user.username))) {
                is CocroResult.Success -> result.value
                is CocroResult.Error -> {
                    // IDEMPOTENT REJOIN: active (JOINED) participant navigating to /play returns full dto
                    if (result.errors.singleOrNull() is SessionError.AlreadyParticipant) {
                        val isActive = session.participants.any {
                            it.userId == user.userId && it.status == ParticipantStatus.JOINED
                        }
                        if (isActive) {
                            logger.info(
                                "User {} already in session {} — returning full dto (idempotent join)",
                                user.userId(), session.shareCode.value,
                            )
                            val gridState = sessionGridStateCache.get(session.id) ?: session.sessionGridState
                            val activeCount = ParticipantsRule.countActiveParticipants(session.participants)
                            return CocroResult.Success(session.toSessionFullDto(gridState, activeCount))
                        }
                    }
                    logger.warn("Session join rejected: {} for session {}", result.errors, session.shareCode.value)
                    return CocroResult.Error(result.errors)
                }
            }

        // PERSISTENCE
        val savedSession = sessionRepository.save(updatedSession)
        val activeParticipantCount = ParticipantsRule.countActiveParticipants(savedSession.participants)

        // Re-activate Redis cache when session resumes from INTERRUPTED
        if (session.status == SessionStatus.INTERRUPTED && savedSession.status == SessionStatus.PLAYING) {
            val gridState = sessionGridStateCache.get(session.id) ?: session.sessionGridState
            sessionGridStateCache.initialize(session.id, gridState)
            logger.info("Session {} cache re-activated (INTERRUPTED → PLAYING)", session.shareCode.value)
        }

        sessionGridStateCache.get(session.id)?.let { gridState ->
            sessionRepository.updateGridState(session.id, gridState)
            sessionGridStateCache.markFlushed(session.id, gridState.revision.value)
        }

        // HEARTBEAT
        heartbeatTracker.markActive(session.id, user.userId)

        // NOTIFICATION
        sessionNotifier.broadcast(
            savedSession.shareCode,
            SessionEvent.ParticipantJoined(userId = user.userId(), username = user.username, participantCount = activeParticipantCount),
        )

        logger.info(
            "User {} successfully joined session {} ({} participants)",
            user.userId(), savedSession.shareCode.value, activeParticipantCount,
        )
        val gridState = sessionGridStateCache.get(savedSession.id) ?: savedSession.sessionGridState
        val activeCount = ParticipantsRule.countActiveParticipants(savedSession.participants)
        return CocroResult.Success(savedSession.toSessionFullDto(gridState, activeCount))
    }
}
