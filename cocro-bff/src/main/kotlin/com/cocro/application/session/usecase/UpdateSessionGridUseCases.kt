package com.cocro.application.session.usecase

import com.cocro.application.auth.port.CurrentUserProvider
import com.cocro.application.session.dto.SessionGridUpdateSuccess
import com.cocro.application.session.dto.UpdateSessionGridDto
import com.cocro.application.session.mapper.toCommand
import com.cocro.application.session.mapper.toSuccess
import com.cocro.application.session.port.SessionGridStateCache
import com.cocro.application.session.port.SessionRepository
import com.cocro.application.session.validation.validateUpdateSessionGridDto
import com.cocro.kernel.common.CocroResult
import com.cocro.kernel.session.error.SessionError
import com.cocro.kernel.session.model.valueobject.SessionShareCode
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class UpdateSessionGridUseCases(
    private val currentUserProvider: CurrentUserProvider,
    private val sessionRepository: SessionRepository,
    private val sessionGridStateCache: SessionGridStateCache,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun execute(dto: UpdateSessionGridDto): CocroResult<SessionGridUpdateSuccess, SessionError> {
        // EARLY AUTH CHECK
        val user =
            currentUserProvider.currentUserOrNull()
                ?: run {
                    logger.warn(
                        "Session grid update rejected: user not authenticated for shareCode={}, pos=({},{}), commandType={}",
                        dto.shareCode,
                        dto.posX,
                        dto.posY,
                        dto.commandType,
                    )
                    return CocroResult.Error(listOf(SessionError.Unauthorized))
                }

        // VALIDATION
        val errors = validateUpdateSessionGridDto(dto)
        if (errors.isNotEmpty()) {
            logger.warn(
                "Session grid update rejected: {} validation errors for shareCode={}, pos=({},{}), commandType={}",
                errors.size,
                dto.shareCode,
                dto.posX,
                dto.posY,
                dto.commandType,
            )
            return CocroResult.Error(errors)
        }

        val sessionShareCode = SessionShareCode(dto.shareCode)

        // BUSINESS RULES
        val sessionGridState =
            sessionGridStateCache.load(sessionShareCode)
                ?: sessionRepository.findByShareCode(sessionShareCode)?.sessionGridState
                ?: run {
                    logger.warn("Session start rejected: session not found with shareCode={}", sessionShareCode)
                    return CocroResult.Error(listOf(SessionError.SessionNotFound(sessionShareCode.toString())))
                }

//        val isUserActiveInSession = session.participants
//            .any { it.userId == user.userId && it.status == InviteStatus.JOINED }
//        if (!isUserActiveInSession) {
//            logger.warn("Session grid update rejected: user {} is not an active participant in session with shareCode={}", user.userId, sessionShareCode)
//            return CocroResult.Error(listOf(SessionError.NotInSession(user.userId())))
//        }

        // MAPPING
        val command = dto.toCommand(user.userId)

        // APPLY COMMAND
        sessionGridState.apply(command)

        // PERSISTENCE
        sessionGridStateCache.save(sessionGridState)

        // BROADCAST (FUTURE: only broadcast the command to other participants, not the whole state)

        // SUCCESS
        return CocroResult.Success(dto.toSuccess())
    }
}
