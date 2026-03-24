package com.cocro.application.session.usecase

import com.cocro.application.auth.port.CurrentUserProvider
import com.cocro.application.grid.port.GridRepository
import com.cocro.application.session.dto.GridTemplateDto
import com.cocro.application.session.mapper.toGridTemplateDto
import com.cocro.application.session.port.SessionRepository
import com.cocro.kernel.common.CocroResult
import com.cocro.kernel.session.enum.InviteStatus
import com.cocro.kernel.session.error.SessionError
import com.cocro.kernel.session.model.valueobject.SessionShareCode
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class GetSessionGridTemplateUseCase(
    private val currentUserProvider: CurrentUserProvider,
    private val sessionRepository: SessionRepository,
    private val gridRepository: GridRepository,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun execute(shareCode: String): CocroResult<GridTemplateDto, SessionError> {
        // 1. Auth
        val user =
            currentUserProvider.currentUserOrNull()
                ?: return CocroResult.Error(listOf(SessionError.Unauthorized))

        // 2. Validate shareCode format
        val sessionShareCode =
            runCatching { SessionShareCode(shareCode) }.getOrElse {
                return CocroResult.Error(listOf(SessionError.InvalidShareCode(shareCode)))
            }

        // 3. Load session
        val session =
            sessionRepository.findByShareCode(sessionShareCode)
                ?: run {
                    logger.warn("Grid template rejected: session not found shareCode={}", shareCode)
                    return CocroResult.Error(listOf(SessionError.SessionNotFound(shareCode)))
                }

        // 4. Verify participant
        if (!session.participants.any { it.userId == user.userId && it.status == InviteStatus.JOINED }) {
            logger.warn(
                "Grid template rejected: user={} is not a participant of session={}",
                user.userId(),
                shareCode,
            )
            return CocroResult.Error(listOf(SessionError.UserNotParticipant(user.userId(), shareCode)))
        }

        // 5. Load reference grid
        val grid =
            gridRepository.findByShortId(session.gridId)
                ?: run {
                    logger.error(
                        "Grid template failed: reference grid not found shareCode={} gridCode={}",
                        shareCode,
                        session.gridId.value,
                    )
                    return CocroResult.Error(listOf(SessionError.ReferenceGridNotFound(session.gridId.value)))
                }

        // 6. Map to DTO (letters stripped)
        val dto = grid.toGridTemplateDto()

        logger.debug("Grid template served for user={} session={}", user.userId(), shareCode)
        return CocroResult.Success(dto)
    }
}
