package com.cocro.application.session.usecase

import com.cocro.application.auth.port.CurrentUserProvider
import com.cocro.application.session.dto.StartSessionDto
import com.cocro.application.session.dto.StartSessionSuccess
import com.cocro.application.session.mapper.toStartSessionSuccess
import com.cocro.application.session.port.SessionRepository
import com.cocro.application.session.validation.validateStartSessionDto
import com.cocro.kernel.common.CocroResult
import com.cocro.kernel.session.enum.SessionStatus
import com.cocro.kernel.session.error.SessionError
import com.cocro.kernel.session.model.valueobject.SessionShareCode
import com.cocro.kernel.session.rule.ParticipantsRule
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class StartSessionUseCase(
    private val currentUserProvider: CurrentUserProvider,
    private val sessionRepository: SessionRepository,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun execute(dto: StartSessionDto): CocroResult<StartSessionSuccess, SessionError> {
        // EARLY AUTH CHECK
        val user =
            currentUserProvider.currentUserOrNull()
                ?: run {
                    logger.warn("Session start rejected: user not authenticated")
                    return CocroResult.Error(listOf(SessionError.Unauthorized))
                }

        // VALIDATION
        val errors = validateStartSessionDto(dto)
        if (errors.isNotEmpty()) {
            logger.warn("Session start rejected: {} validation errors for shareCode={}", errors.size, dto.shareCode)
            return CocroResult.Error(errors)
        }

        // MAPPING
        val sessionShareCode = SessionShareCode(dto.shareCode)

        // BUSINESS RULES
        val session =
            sessionRepository.findByShareCode(sessionShareCode)
                ?: run {
                    logger.warn("Session start rejected: session not found with shareCode={}", sessionShareCode)
                    return CocroResult.Error(listOf(SessionError.SessionNotFound(sessionShareCode.toString())))
                }

        if (session.status != SessionStatus.CREATING) {
            logger.warn(
                "Session start rejected: invalid status {} for session {} (expected CREATING)",
                session.status,
                session.shareCode.value,
            )
            return CocroResult.Error(listOf(SessionError.InvalidStatusForAction(session.status, "start")))
        }

        val activeCount = ParticipantsRule.countActiveParticipants(session.participants)
        if (activeCount < 1) {
            logger.warn(
                "Session start rejected: not enough participants in session {} ({}/1 minimum)",
                session.shareCode.value,
                activeCount,
            )
            return CocroResult.Error(listOf(SessionError.NotEnoughParticipants))
        }

        // DOMAIN
        val startedSession = session.startPlaying()

        // PERSISTENCE
        val savedSession = sessionRepository.save(startedSession)

        // SUCCESS
        logger.info(
            "Session {} successfully started by user {} with {} participants",
            savedSession.shareCode.value,
            user.userId(),
            activeCount,
        )
        return CocroResult.Success(savedSession.toStartSessionSuccess())
    }
}
