package com.cocro.application.session.usecase

import com.cocro.application.auth.port.CurrentUserProvider
import com.cocro.application.grid.port.GridRepository
import com.cocro.application.session.dto.GridCheckSuccess
import com.cocro.application.session.dto.notification.SessionEvent
import com.cocro.application.session.port.SessionGridStateCache
import com.cocro.application.session.port.SessionNotifier
import com.cocro.application.session.port.SessionRepository
import com.cocro.kernel.common.CocroResult
import com.cocro.kernel.session.enum.InviteStatus
import com.cocro.kernel.session.enum.SessionStatus
import com.cocro.kernel.session.error.SessionError
import com.cocro.kernel.session.model.valueobject.SessionShareCode
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class CheckGridUseCase(
    private val currentUserProvider: CurrentUserProvider,
    private val sessionRepository: SessionRepository,
    private val sessionGridStateCache: SessionGridStateCache,
    private val gridRepository: GridRepository,
    private val sessionNotifier: SessionNotifier,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun execute(shareCode: String): CocroResult<GridCheckSuccess, SessionError> {
        // 1. Auth
        val user =
            currentUserProvider.currentUserOrNull()
                ?: run {
                    logger.warn("Grid check rejected: user not authenticated")
                    return CocroResult.Error(listOf(SessionError.Unauthorized))
                }

        // 2. Validate shareCode format
        val sessionShareCode =
            runCatching { SessionShareCode(shareCode) }.getOrElse {
                return CocroResult.Error(listOf(SessionError.InvalidShareCode(shareCode)))
            }

        // 3. Load session
        val session =
            sessionRepository.findByShareCode(sessionShareCode)
                ?: run {
                    logger.warn("Grid check rejected: session not found shareCode={}", shareCode)
                    return CocroResult.Error(listOf(SessionError.SessionNotFound(shareCode)))
                }

        // 4. Verify participant
        if (!session.participants.any { it.userId == user.userId && it.status == InviteStatus.JOINED }) {
            logger.warn("Grid check rejected: user={} is not a participant of session={}", user.userId(), shareCode)
            return CocroResult.Error(listOf(SessionError.UserNotParticipant(user.userId(), shareCode)))
        }

        // 5. Verify session is PLAYING
        if (session.status != SessionStatus.PLAYING) {
            logger.warn("Grid check rejected: session={} status={} (expected PLAYING)", shareCode, session.status)
            return CocroResult.Error(listOf(SessionError.InvalidStatusForAction(session.status, "check-grid")))
        }

        // 6. Load current grid state — cache first, fallback to embedded state
        val gridState = sessionGridStateCache.get(session.id) ?: session.sessionGridState

        // 7. Load reference grid
        val referenceGrid =
            gridRepository.findByShortId(gridState.gridShareCode)
                ?: run {
                    logger.error(
                        "Grid check failed: reference grid not found shareCode={} gridCode={}",
                        shareCode, gridState.gridShareCode.value,
                    )
                    return CocroResult.Error(listOf(SessionError.ReferenceGridNotFound(gridState.gridShareCode.value)))
                }

        // 8. Domain check — pure comparison, no side effects
        val result = gridState.checkAgainst(referenceGrid)

        // Flush state to Mongo on every check
        sessionRepository.updateGridState(session.id, gridState)
        sessionGridStateCache.markFlushed(session.id, gridState.revision.value)

        // Broadcast check result to all participants
        sessionNotifier.broadcast(
            session.shareCode,
            SessionEvent.GridChecked(
                userId = user.userId(),
                isComplete = result.isComplete,
                correctCount = result.correctCount,
                totalCount = result.totalCount,
            ),
        )

        logger.info(
            "Grid check session={} complete={} correct={} wrong={} filled={}/{}",
            shareCode, result.isComplete, result.isCorrect, result.wrongCount, result.filledCount, result.totalCount,
        )

        // End-of-game: complete AND correct → end session
        if (result.isComplete && result.isCorrect) {
            when (val endResult = session.end()) {
                is CocroResult.Success -> {
                    sessionRepository.save(endResult.value)
                    sessionNotifier.broadcast(
                        session.shareCode,
                        SessionEvent.SessionEnded(
                            shareCode = shareCode,
                            correctCount = result.correctCount,
                            totalCount = result.totalCount,
                        ),
                    )
                    logger.info("Session {} ended: grid complete and correct ({}/{})", shareCode, result.correctCount, result.totalCount)
                }
                is CocroResult.Error -> {
                    logger.warn("Could not end session {}: {}", shareCode, endResult.errors)
                }
            }
        }

        return CocroResult.Success(
            GridCheckSuccess(
                shareCode = shareCode,
                isComplete = result.isComplete,
                isCorrect = result.isCorrect,
                filledCount = result.filledCount,
                totalCount = result.totalCount,
                wrongCount = result.wrongCount,
                correctCount = result.correctCount,
            ),
        )
    }
}
