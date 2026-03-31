package com.cocro.application.session.usecase

import com.cocro.application.session.port.SessionGridStateCache
import com.cocro.application.session.port.SessionRepository
import com.cocro.domain.auth.model.valueobject.UserId
import com.cocro.domain.common.CocroResult
import com.cocro.domain.session.error.SessionError
import com.cocro.domain.session.model.valueobject.SessionShareCode
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class DeleteSessionUseCase(
    private val sessionRepository: SessionRepository,
    private val sessionGridStateCache: SessionGridStateCache,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun execute(shareCode: String, actorId: UserId): CocroResult<Unit, SessionError> {
        val session = sessionRepository.findByShareCode(SessionShareCode(shareCode))
            ?: return CocroResult.Error(listOf(SessionError.SessionNotFound(shareCode)))

        if (session.author.id != actorId) {
            return CocroResult.Error(listOf(SessionError.NotCreator(actorId.toString(), shareCode)))
        }

        sessionGridStateCache.deactivate(session.id)
        sessionRepository.deleteById(session.id)

        logger.info("Session {} deleted by user {}", shareCode, actorId)
        return CocroResult.Success(Unit)
    }
}

