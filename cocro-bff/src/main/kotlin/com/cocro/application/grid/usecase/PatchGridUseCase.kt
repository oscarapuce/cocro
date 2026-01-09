package com.cocro.application.grid.usecase

import com.cocro.application.auth.port.CurrentUserProvider
import com.cocro.application.grid.dto.PatchGridDto
import com.cocro.application.grid.mapper.applyPatchTo
import com.cocro.application.grid.port.GridRepository
import com.cocro.application.grid.validation.validatePatchGrid
import com.cocro.domain.grid.valueobject.GridId
import com.cocro.kernel.common.CocroResult
import com.cocro.kernel.grid.error.GridError
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class PatchGridUseCase(
    private val currentUserProvider: CurrentUserProvider,
    private val gridRepository: GridRepository,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun execute(dto: PatchGridDto): CocroResult<GridId, GridError> {
        // VALIDATION
        val errors = validatePatchGrid(dto)
        if (errors.isNotEmpty()) {
            logger.warn("Grid submission rejected: {} errors", errors.size)
            return CocroResult.Error(errors)
        }

        // AUTHORIZATION
        val previousGrid =
            gridRepository.findByShortId(dto.gridId)
                ?: return CocroResult.Error(listOf(GridError.GridNotFound(dto.gridId)))
        val user = currentUserProvider.currentUserOrNull()
        if (user == null || (previousGrid.metadata.author != user.userId && !user.isAdmin())) {
            return CocroResult.Error(listOf(GridError.UnauthorizedGridModification(dto.gridId)))
        }

        // APPLY PATCH
        val grid = dto.applyPatchTo(previousGrid)

        // CHECK
        val otherGrid = gridRepository.findByHashLetters(grid.hashLetters)
        if (otherGrid != null) {
            logger.warn("Duplicate grid detected (hash={} for gridId={})", otherGrid.hashLetters, otherGrid.shortId)
            return CocroResult.Error(listOf(GridError.DuplicateLetterHash(otherGrid.shortId.value)))
        }

        // PERSISTENCE
        val savedGrid = gridRepository.save(grid)

        // SUCCESS
        logger.info("Grid {} successfully patched", savedGrid.shortId.value)
        return CocroResult.Success(savedGrid.shortId)
    }
}
