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
import com.cocro.domain.common.CocroResult
import com.cocro.domain.session.enum.SessionStatus
import com.cocro.domain.session.error.SessionError
import com.cocro.domain.session.model.SessionLifecycleCommand
import com.cocro.domain.session.model.valueobject.SessionShareCode
import com.cocro.domain.session.rule.ParticipantsRule
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
        val user =
            currentUserProvider.currentUserOrNull()
                ?: run {
                    logger.warn("Session leave rejected: user not authenticated")
                    return CocroResult.Error(listOf(SessionError.Unauthorized))
                }

        val errors = validateLeaveSessionDto(leaveSessionDto)
        if (errors.isNotEmpty()) {
            logger.warn("Session leave rejected: {} validation errors for shareCode={}", errors.size, leaveSessionDto.shareCode)
            return CocroResult.Error(errors)
        }

        val sessionShareCode = SessionShareCode(leaveSessionDto.shareCode)
        val session =
            sessionRepository.findByShareCode(sessionShareCode)
                ?: run {
                    logger.warn("Session leave rejected: session not found with shareCode={}", sessionShareCode)
                    return CocroResult.Error(listOf(SessionError.SessionNotFound(sessionShareCode.toString())))
                }

        // DOMAIN COMMAND (validates user is a joined participant)
        val updatedSession =
            when (val result = session.apply(SessionLifecycleCommand.Leave(user.userId))) {
                is CocroResult.Success -> result.value
                is CocroResult.Error -> {
                    logger.warn("Session leave rejected: {} for session {}", result.errors, session.shareCode.value)
                    return CocroResult.Error(result.errors)
                }
            }

        // PERSISTENCE
        sessionRepository.save(updatedSession)

        sessionGridStateCache.get(session.id)?.let { gridState ->
            sessionRepository.updateGridState(session.id, gridState)
            sessionGridStateCache.markFlushed(session.id, gridState.revision.value)
        }

        // Detect: last active participant left → INTERRUPTED
        val activeCount = ParticipantsRule.countActiveParticipants(updatedSession.participants)
        if (activeCount == 0 && updatedSession.status == SessionStatus.PLAYING) {
            val interrupted = updatedSession.interrupt()
            sessionRepository.save(interrupted)
            sessionGridStateCache.deactivate(session.id)
            sessionNotifier.broadcast(
                interrupted.shareCode,
                SessionEvent.SessionInterrupted(shareCode = interrupted.shareCode.value),
            )
            logger.info("Session {} interrupted: all participants left", sessionShareCode.value)
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
