package com.cocro.application.session.usecase

import com.cocro.application.auth.port.CurrentUserProvider
import com.cocro.application.grid.port.GridRepository
import com.cocro.application.session.dto.CreateSessionDto
import com.cocro.application.session.dto.SessionCreationSuccess
import com.cocro.application.session.mapper.toGridTemplateSnapshot
import com.cocro.application.session.mapper.toSessionCreationSuccess
import com.cocro.application.session.port.SessionGridStateCache
import com.cocro.application.session.port.SessionRepository
import com.cocro.application.session.service.SessionCodeGenerator
import com.cocro.application.session.validation.validateCreateSessionDto
import com.cocro.kernel.common.CocroResult
import com.cocro.kernel.grid.model.valueobject.GridShareCode
import com.cocro.kernel.session.error.SessionError
import com.cocro.kernel.session.model.Session
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class CreateSessionUseCase(
    private val currentUserProvider: CurrentUserProvider,
    private val sessionRepository: SessionRepository,
    private val sessionGridStateCache: SessionGridStateCache,
    private val shareCodeGenerator: SessionCodeGenerator,
    private val gridRepository: GridRepository,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun execute(createSessionDto: CreateSessionDto): CocroResult<SessionCreationSuccess, SessionError> {
        val user =
            currentUserProvider.currentUserOrNull()
                ?: run {
                    logger.warn("Session creation rejected: user not authenticated")
                    return CocroResult.Error(listOf(SessionError.Unauthorized))
                }

        val errors = validateCreateSessionDto(createSessionDto)
        if (errors.isNotEmpty()) {
            logger.warn("Session creation rejected: {} validation errors for gridId={}", errors.size, createSessionDto.gridId)
            return CocroResult.Error(errors)
        }

        val gridId = GridShareCode(createSessionDto.gridId)
        val grid =
            gridRepository.findByShortId(gridId)
                ?: run {
                    logger.warn("Session creation rejected: grid not found with id={}", gridId.value)
                    return CocroResult.Error(listOf(SessionError.ReferenceGridNotFound(gridId.value)))
                }

        val session =
            Session.create(
                creatorId = user.userId,
                shareCode = shareCodeGenerator.generateId(),
                gridId = gridId,
                gridTemplate = grid.toGridTemplateSnapshot(),
            )
        val savedSession = sessionRepository.save(session)
        sessionGridStateCache.initialize(savedSession.id, savedSession.sessionGridState)

        logger.info("Session {} successfully created by user {} for grid {}", savedSession.shareCode.value, user.userId(), gridId.value)
        return CocroResult.Success(savedSession.toSessionCreationSuccess())
    }
}
