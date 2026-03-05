package com.cocro.application.session.usecase

import com.cocro.application.auth.port.CurrentUserProvider
import com.cocro.application.session.dto.CellStateDto
import com.cocro.application.session.dto.SessionStateDto
import com.cocro.application.session.port.SessionGridStateCache
import com.cocro.application.session.port.SessionRepository
import com.cocro.kernel.common.CocroResult
import com.cocro.kernel.session.enum.InviteStatus
import com.cocro.kernel.session.error.SessionError
import com.cocro.kernel.session.model.state.SessionGridCellState
import com.cocro.kernel.session.model.valueobject.SessionShareCode
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class GetSessionStateUseCase(
    private val currentUserProvider: CurrentUserProvider,
    private val sessionRepository: SessionRepository,
    private val sessionGridStateCache: SessionGridStateCache,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun execute(shareCode: String): CocroResult<SessionStateDto, SessionError> {
        val user =
            currentUserProvider.currentUserOrNull()
                ?: return CocroResult.Error(listOf(SessionError.Unauthorized))

        val sessionShareCode = runCatching { SessionShareCode(shareCode) }.getOrElse {
            return CocroResult.Error(listOf(SessionError.InvalidShareCode(shareCode)))
        }

        val session =
            sessionRepository.findByShareCode(sessionShareCode)
                ?: run {
                    logger.warn("State resync rejected: session not found with shareCode={}", shareCode)
                    return CocroResult.Error(listOf(SessionError.SessionNotFound(shareCode)))
                }

        if (!session.participants.any { it.userId == user.userId && it.status == InviteStatus.JOINED }) {
            logger.warn("State resync rejected: user {} is not a participant of session {}", user.userId(), shareCode)
            return CocroResult.Error(listOf(SessionError.UserNotParticipant(user.userId(), shareCode)))
        }

        // Always serve from cache — fall back to the session's embedded state if cache miss
        val gridState = sessionGridStateCache.get(session.id) ?: session.sessionGridState

        val dto = SessionStateDto(
            sessionId = session.id.toString(),
            shareCode = session.shareCode.value,
            revision = gridState.revision.value,
            cells = gridState.cells.map { (pos, state) ->
                CellStateDto(
                    x = pos.x,
                    y = pos.y,
                    letter = (state as SessionGridCellState.Letter).value,
                )
            },
        )

        logger.debug("Resync served for user={} session={} revision={}", user.userId(), shareCode, gridState.revision.value)
        return CocroResult.Success(dto)
    }
}
