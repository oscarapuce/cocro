package com.cocro.application.grid.usecase

import com.cocro.application.auth.port.CurrentUserProvider
import com.cocro.application.grid.dto.SubmitGridDto
import com.cocro.application.grid.mapper.toDomain
import com.cocro.application.grid.port.GridRepository
import com.cocro.application.grid.service.GridIdGenerator
import com.cocro.application.grid.validation.validateSubmitGrid
import com.cocro.domain.grid.valueobject.GridId
import com.cocro.kernel.common.Result
import com.cocro.kernel.grid.error.GridError
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class SubmitGridUseCase(
    private val currentUserProvider: CurrentUserProvider,
    private val gridRepository: GridRepository,
    private val gridIdGenerator: GridIdGenerator,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun execute(dto: SubmitGridDto): Result<GridId, GridError> {
        val errors = validateSubmitGrid(dto)

        val user = currentUserProvider.currentUserOrNull()

        if (errors.isNotEmpty()) {
            logger.warn("Grid submission rejected: {} errors", errors.size)
            return Result.Error(errors)
        }

        val grid = dto.toDomain(gridIdGenerator.generateId())

        val otherGrid = gridRepository.findByHashLetters(grid.hashLetters)
        if (otherGrid != null) {
            logger.warn("Duplicate grid detected (hash={} for gridId={})", otherGrid.hashLetters, otherGrid.shortId)
            return Result.Error(listOf(GridError.DuplicateLetterHash(otherGrid.shortId.value)))
        }

        gridRepository.save(grid)

        logger.info("Grid {} successfully submitted", grid.shortId.value)
        return Result.Success(grid.shortId)
    }
}
