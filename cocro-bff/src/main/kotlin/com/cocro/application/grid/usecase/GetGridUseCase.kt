package com.cocro.application.grid.usecase

import com.cocro.application.grid.dto.GridFullDto
import com.cocro.application.grid.mapper.toFullDto
import com.cocro.application.grid.port.GridRepository
import com.cocro.domain.common.CocroResult
import com.cocro.domain.grid.error.GridError
import com.cocro.domain.grid.model.valueobject.GridShareCode
import com.cocro.domain.grid.rule.GridShareCodeRule
import org.springframework.stereotype.Service

@Service
class GetGridUseCase(
    private val gridRepository: GridRepository,
) {
    fun execute(shortId: String): CocroResult<GridFullDto, GridError> {
        if (!GridShareCodeRule.validate(shortId)) {
            return CocroResult.Error(listOf(GridError.InvalidGridId(shortId)))
        }
        val shareCode = GridShareCode(shortId)
        val grid = gridRepository.findByShortId(shareCode)
            ?: return CocroResult.Error(listOf(GridError.GridNotFound(shortId)))
        return CocroResult.Success(grid.toFullDto())
    }
}
