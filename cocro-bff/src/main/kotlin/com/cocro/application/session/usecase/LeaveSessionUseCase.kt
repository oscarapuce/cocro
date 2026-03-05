package com.cocro.application.session.usecase

import com.cocro.application.auth.port.CurrentUserProvider
import com.cocro.application.session.dto.LeaveSessionDto
import com.cocro.application.session.dto.SessionLeaveSuccess
import com.cocro.application.session.dto.notification.SessionEvent
import com.cocro.application.session.mapper.toSessionLeaveSuccess
import com.cocro.application.session.port.HeartbeatTracker
import com.cocro.application.session.port.SessionGridStateCache
import com.cocro.application.session.port.SessionNotifier
import com.cocro.application.session.port.SessionRepository
import com.cocro.application.session.validation.validateLeaveSessionDto
import com.cocro.kernel.common.CocroResult
import com.cocro.kernel.session.enum.InviteStatus
import com.cocro.kernel.session.error.SessionError
import com.cocro.kernel.session.model.valueobject.SessionShareCode
import com.cocro.kernel.session.rule.ParticipantsRule
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class LeaveSessionUseCase(
    private val currentUserProvider: CurrentUserProvider,
    private val sessionRepository: SessionRepository,
    private val sessionGridStateCache: SessionGridStateCache,
    private val sessionNotifier: SessionNotifier,
    private val heartbeatTracker: HeartbeatTracker,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun execute(leaveSessionDto: LeaveSessionDto): CocroResult<SessionLeaveSuccess, SessionError> {
        // EARLY AUTH CHECK
        val user =
            currentUserProvider.currentUserOrNull()
                ?: run {
                    logger.warn("Session leave rejected: user not authenticated")
                    return CocroResult.Error(listOf(SessionError.Unauthorized))
                }

        // VALIDATION
        val errors = validateLeaveSessionDto(leaveSessionDto)
        if (errors.isNotEmpty()) {
            logger.warn("Session leave rejected: {} validation errors for shareCode={}", errors.size, leaveSessionDto.shareCode)
            return CocroResult.Error(errors)
        }

        val sessionShareCode = SessionShareCode(leaveSessionDto.shareCode)

        // CHECK BUSINESS RULES
        val session =
            sessionRepository.findByShareCode(sessionShareCode)
                ?: run {
                    logger.warn("Session leave rejected: session not found with shareCode={}", sessionShareCode)
                    return CocroResult.Error(listOf(SessionError.SessionNotFound(sessionShareCode.toString())))
                }

        if (!session.participants.any { it.userId == user.userId && it.status == InviteStatus.JOINED }) {
            logger.warn("Session leave rejected: user {} is not a participant of session {}", user.userId(), sessionShareCode.value)
            return CocroResult.Error(listOf(SessionError.UserNotParticipant(user.userId(), sessionShareCode.value)))
        }

        // APPLY AND PERSISTENCE
        val updatedSession = session.leave(user.userId)
        sessionRepository.save(updatedSession)

        // Flush current grid state to Mongo alongside participant update
        sessionGridStateCache.get(session.id)?.let { gridState ->
            sessionRepository.updateGridState(session.id, gridState)
            sessionGridStateCache.markFlushed(session.id, gridState.revision.value)
        }

        // HEARTBEAT CLEANUP
        heartbeatTracker.remove(session.id, user.userId)
        heartbeatTracker.unregisterUserSession(user.userId)

        // NOTIFICATION
        sessionNotifier.broadcast(
            updatedSession.shareCode,
            SessionEvent.ParticipantLeft(
                userId = user.userId(),
                participantCount = ParticipantsRule.countActiveParticipants(updatedSession.participants),
                reason = "explicit",
            ),
        )

        logger.info("User {} left session {}", user.userId(), sessionShareCode.value)
        return CocroResult.Success(updatedSession.toSessionLeaveSuccess())
    }
}
