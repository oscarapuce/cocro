package com.cocro.application.session.usecase

import com.cocro.application.auth.port.CurrentUserProvider
import com.cocro.application.session.dto.SessionGridUpdateSuccess
import com.cocro.application.session.dto.UpdateSessionGridDto
import com.cocro.application.session.dto.notification.SessionEvent
import com.cocro.application.session.mapper.toCommand
import com.cocro.application.session.mapper.toSuccess
import com.cocro.application.session.port.SessionGridStateCache
import com.cocro.application.session.port.SessionNotifier
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
    private val sessionNotifier: SessionNotifier,
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
        val session =
            sessionRepository.findByShareCode(sessionShareCode)
                ?: run {
                    logger.warn("Session grid update rejected: session not found with shareCode={}", sessionShareCode)
                    return CocroResult.Error(listOf(SessionError.SessionNotFound(sessionShareCode.toString())))
                }

        val currentState = sessionGridStateCache.get(session.id) ?: session.sessionGridState

        // MAPPING
        val command = dto.toCommand(session.id, user.userId)

        // APPLY COMMAND
        val newState = currentState.apply(command)

        // PERSISTENCE
        sessionGridStateCache.compareAndSet(session.id, currentState.revision.value, newState)

        // BROADCAST
        sessionNotifier.broadcast(
            sessionShareCode,
            SessionEvent.GridUpdated(
                actorId = user.userId(),
                posX = dto.posX,
                posY = dto.posY,
                commandType = dto.commandType,
                letter = dto.letter,
            ),
        )

        // SUCCESS
        return CocroResult.Success(dto.toSuccess(session.id))
    }
}
