package com.cocro.application.session.usecase

import com.cocro.application.auth.port.CurrentUserProvider
import com.cocro.application.session.dto.StartSessionDto
import com.cocro.application.session.dto.StartSessionSuccess
import com.cocro.application.session.dto.notification.SessionEvent
import com.cocro.application.session.mapper.toStartSessionSuccess
import com.cocro.application.session.port.SessionNotifier
import com.cocro.application.session.port.SessionRepository
import com.cocro.application.session.validation.validateStartSessionDto
import com.cocro.kernel.common.CocroResult
import com.cocro.kernel.session.error.SessionError
import com.cocro.kernel.session.model.SessionLifecycleCommand
import com.cocro.kernel.session.model.valueobject.SessionShareCode
import com.cocro.kernel.session.rule.ParticipantsRule
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class StartSessionUseCase(
    private val currentUserProvider: CurrentUserProvider,
    private val sessionRepository: SessionRepository,
    private val sessionNotifier: SessionNotifier,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun execute(dto: StartSessionDto): CocroResult<StartSessionSuccess, SessionError> {
        val user =
            currentUserProvider.currentUserOrNull()
                ?: run {
                    logger.warn("Session start rejected: user not authenticated")
                    return CocroResult.Error(listOf(SessionError.Unauthorized))
                }

        val errors = validateStartSessionDto(dto)
        if (errors.isNotEmpty()) {
            logger.warn("Session start rejected: {} validation errors for shareCode={}", errors.size, dto.shareCode)
            return CocroResult.Error(errors)
        }

        val sessionShareCode = SessionShareCode(dto.shareCode)
        val session =
            sessionRepository.findByShareCode(sessionShareCode)
                ?: run {
                    logger.warn("Session start rejected: session not found with shareCode={}", sessionShareCode)
                    return CocroResult.Error(listOf(SessionError.SessionNotFound(sessionShareCode.toString())))
                }

        // DOMAIN COMMAND (validates status and minimum participant count)
        val startedSession =
            when (val result = session.apply(SessionLifecycleCommand.Start(user.userId))) {
                is CocroResult.Success -> result.value
                is CocroResult.Error -> {
                    logger.warn("Session start rejected: {} for session {}", result.errors, session.shareCode.value)
                    return CocroResult.Error(result.errors)
                }
            }

        // PERSISTENCE
        val savedSession = sessionRepository.save(startedSession)
        val activeCount = ParticipantsRule.countActiveParticipants(savedSession.participants)

        // NOTIFICATION
        sessionNotifier.broadcast(savedSession.shareCode, SessionEvent.SessionStarted(participantCount = activeCount))

        logger.info(
            "Session {} successfully started by user {} with {} participants",
            savedSession.shareCode.value, user.userId(), activeCount,
        )
        return CocroResult.Success(savedSession.toStartSessionSuccess())
    }
}
