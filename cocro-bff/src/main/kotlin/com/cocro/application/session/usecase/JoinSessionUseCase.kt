package com.cocro.application.session.usecase

import com.cocro.application.auth.port.CurrentUserProvider
import com.cocro.application.session.dto.JoinSessionDto
import com.cocro.application.session.dto.SessionJoinSuccess
import com.cocro.application.session.mapper.toSessionJoinSuccess
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

        // MAPPING
        val sessionShareCode = SessionShareCode(joinSessionDto.shareCode)

        // CHECK BUSINESS RULES (FUTURE: check if user is banned or invited)
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

        // SUCCESS
        logger.info(
            "User {} successfully joined session {} ({} participants)",
            user.userId(),
            savedSession.shareCode.value,
            ++activeParticipantCount,
        )
        return CocroResult.Success(savedSession.toSessionJoinSuccess())
    }
}
