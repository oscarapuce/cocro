package com.cocro.application.grid.usecase

import com.cocro.application.auth.port.CurrentUserProvider
import com.cocro.application.grid.dto.SubmitGridDto
import com.cocro.application.grid.mapper.toDomain
import com.cocro.application.grid.port.GridRepository
import com.cocro.application.grid.service.GridIdGenerator
import com.cocro.application.grid.validation.validateSubmitGrid
import com.cocro.domain.common.CocroResult
import com.cocro.domain.common.model.Author
import com.cocro.domain.grid.error.GridError
import com.cocro.domain.grid.model.valueobject.GridShareCode
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class SubmitGridUseCase(
    private val currentUserProvider: CurrentUserProvider,
    private val gridRepository: GridRepository,
    private val gridIdGenerator: GridIdGenerator,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun execute(dto: SubmitGridDto): CocroResult<GridShareCode, GridError> {
        // VALIDATION
        val errors = validateSubmitGrid(dto)
        if (errors.isNotEmpty()) {
            logger.warn("Grid submission rejected: {} errors", errors.size)
            return CocroResult.Error(errors)
        }

        // MAPPING
        val user =
            currentUserProvider.currentUserOrNull()
                ?: run {
                    logger.warn("Grid submission rejected: user not authenticated")
                    return CocroResult.Error(listOf(GridError.UnauthorizedGridCreation))
                }

        val shortId = gridIdGenerator.generateId()
        val grid = dto.toDomain(shortId, Author(id = user.userId, username = user.username))

        // CHECK
        val otherGrid = gridRepository.findByHashLetters(grid.hashLetters)
        if (otherGrid != null) {
            logger.warn("Duplicate grid detected (hash={} for gridId={})", otherGrid.hashLetters, otherGrid.shortId)
            return CocroResult.Error(listOf(GridError.DuplicateLetterHash(otherGrid.shortId.value)))
        }

        // PERSISTENCE
        val savedGrid = gridRepository.save(grid)

        // SUCCESS
        logger.info("Grid {} successfully submitted", savedGrid.shortId.value)
        return CocroResult.Success(savedGrid.shortId)
    }
}
