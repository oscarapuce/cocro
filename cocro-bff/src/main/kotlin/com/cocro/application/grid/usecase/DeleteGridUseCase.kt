package com.cocro.application.grid.usecase

import com.cocro.application.auth.port.CurrentUserProvider
import com.cocro.application.grid.port.GridRepository
import com.cocro.application.session.port.SessionRepository
import com.cocro.domain.common.CocroResult
import com.cocro.domain.grid.error.GridError
import com.cocro.domain.grid.model.valueobject.GridShareCode
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class DeleteGridUseCase(
    private val currentUserProvider: CurrentUserProvider,
    private val gridRepository: GridRepository,
    private val sessionRepository: SessionRepository,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun execute(shortId: String): CocroResult<Unit, GridError> {
        val user = currentUserProvider.currentUserOrNull()
            ?: return CocroResult.Error(listOf(GridError.UnauthorizedGridCreation))

        val gridShareCode = GridShareCode(shortId)
        val grid = gridRepository.findByShortId(gridShareCode)
            ?: return CocroResult.Error(listOf(GridError.GridNotFound(shortId)))

        if (grid.metadata.author.id != user.userId) {
            return CocroResult.Error(listOf(GridError.UnauthorizedGridModification(shortId)))
        }

        if (sessionRepository.existsByGridIdAndActiveStatus(gridShareCode)) {
            return CocroResult.Error(listOf(GridError.GridHasActiveSessions))
        }

        gridRepository.deleteByShortId(gridShareCode)
        logger.info("Grid {} deleted by user {}", shortId, user.userId())
        return CocroResult.Success(Unit)
    }
}
