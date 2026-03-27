package com.cocro.application.session.usecase

import com.cocro.application.auth.port.CurrentUserProvider
import com.cocro.application.session.dto.SessionFullDto
import com.cocro.application.session.mapper.toSessionFullDto
import com.cocro.application.session.port.SessionGridStateCache
import com.cocro.application.session.port.SessionRepository
import com.cocro.domain.common.CocroResult
import com.cocro.domain.session.enum.InviteStatus
import com.cocro.domain.session.error.SessionError
import com.cocro.domain.session.model.valueobject.SessionShareCode
import com.cocro.domain.session.rule.ParticipantsRule
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class SynchroniseSessionUseCase(
    private val currentUserProvider: CurrentUserProvider,
    private val sessionRepository: SessionRepository,
    private val sessionGridStateCache: SessionGridStateCache,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun execute(shareCode: String): CocroResult<SessionFullDto, SessionError> {
        val user = currentUserProvider.currentUserOrNull()
            ?: return CocroResult.Error(listOf(SessionError.Unauthorized))

        val sessionShareCode = runCatching { SessionShareCode(shareCode) }.getOrElse {
            return CocroResult.Error(listOf(SessionError.InvalidShareCode(shareCode)))
        }

        val session = sessionRepository.findByShareCode(sessionShareCode)
            ?: return CocroResult.Error(listOf(SessionError.SessionNotFound(shareCode)))

        if (!session.participants.any { it.userId == user.userId && it.status == InviteStatus.JOINED }) {
            logger.warn("Sync rejected: user={} is not a participant of session={}", user.userId(), shareCode)
            return CocroResult.Error(listOf(SessionError.UserNotParticipant(user.userId(), shareCode)))
        }

        val gridState = sessionGridStateCache.get(session.id) ?: session.sessionGridState
        sessionRepository.updateGridState(session.id, gridState)
        sessionGridStateCache.markFlushed(session.id, gridState.revision.value)

        val activeCount = ParticipantsRule.countActiveParticipants(session.participants)
        logger.info("Session {} synchronised by user {}", shareCode, user.userId())
        return CocroResult.Success(session.toSessionFullDto(gridState, activeCount))
    }
}
