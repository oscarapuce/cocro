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
import com.cocro.domain.common.CocroResult
import com.cocro.domain.session.error.SessionError
import com.cocro.domain.session.model.valueobject.SessionShareCode
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

private const val DEFAULT_FLUSH_THRESHOLD = 50L

@Service
class UpdateSessionGridUseCases(
    private val currentUserProvider: CurrentUserProvider,
    private val sessionRepository: SessionRepository,
    private val sessionGridStateCache: SessionGridStateCache,
    private val sessionNotifier: SessionNotifier,
    @Value("\${cocro.session.flush.threshold:$DEFAULT_FLUSH_THRESHOLD}") private val flushThreshold: Long,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun execute(dto: UpdateSessionGridDto): CocroResult<SessionGridUpdateSuccess, SessionError> {
        // EARLY AUTH CHECK
        val user =
            currentUserProvider.currentUserOrNull()
                ?: run {
                    logger.warn(
                        "Session grid update rejected: user not authenticated for shareCode={}, pos=({},{}), commandType={}",
                        dto.shareCode, dto.posX, dto.posY, dto.commandType,
                    )
                    return CocroResult.Error(listOf(SessionError.Unauthorized))
                }

        // VALIDATION
        val errors = validateUpdateSessionGridDto(dto)
        if (errors.isNotEmpty()) {
            logger.warn(
                "Session grid update rejected: {} validation errors for shareCode={}, pos=({},{}), commandType={}",
                errors.size, dto.shareCode, dto.posX, dto.posY, dto.commandType,
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

        val currentState = sessionGridStateCache.get(session.id)
            ?: run {
                // Redis key missing (restart or TTL expiry) — reinitialize from MongoDB state
                logger.info("Redis key missing for session={}, reinitializing from MongoDB", session.shareCode.value)
                sessionGridStateCache.initialize(session.id, session.sessionGridState)
                session.sessionGridState
            }

        // APPLY COMMAND
        val command = dto.toCommand(session.id, user.userId)
        val newState = currentState.apply(command)

        // CACHE UPDATE (atomic, throws on revision conflict)
        try {
            sessionGridStateCache.compareAndSet(session.id, currentState.revision.value, newState)
        } catch (e: IllegalStateException) {
            val currentRevision =
                sessionGridStateCache.get(session.id)?.revision?.value
                    ?: sessionRepository.findByShareCode(sessionShareCode)?.sessionGridState?.revision?.value
                    ?: currentState.revision.value
            logger.warn(
                "CAS conflict for session={}, expectedRevision={}, currentRevision={}",
                session.shareCode.value, currentState.revision.value, currentRevision,
            )
            sessionNotifier.notifyUser(user.userId, SessionEvent.SyncRequired(currentRevision = currentRevision))
            return CocroResult.Error(listOf(SessionError.ConcurrentModification))
        }

        // THRESHOLD FLUSH: persist to MongoDB every N revisions
        val lastFlushed = sessionGridStateCache.getLastFlushedRevision(session.id)
        if (newState.revision.value - lastFlushed >= flushThreshold) {
            sessionRepository.updateGridState(session.id, newState)
            sessionGridStateCache.markFlushed(session.id, newState.revision.value)
            logger.debug(
                "Threshold flush: session={}, revision={}",
                session.shareCode.value,
                newState.revision.value,
            )
        }

        // BROADCAST
        sessionNotifier.broadcast(
            sessionShareCode,
            SessionEvent.GridUpdated(
                actorId = user.userId(),
                posX = dto.posX,
                posY = dto.posY,
                commandType = dto.commandType,
                letter = dto.letter,
                revision = newState.revision.value,
            ),
        )

        return CocroResult.Success(dto.toSuccess(session.id))
    }
}
