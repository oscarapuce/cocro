package com.cocro.application.grid.usecase

import com.cocro.application.auth.port.CurrentUserProvider
import com.cocro.application.grid.dto.GridSummaryDto
import com.cocro.application.grid.mapper.toSummaryDto
import com.cocro.application.grid.port.GridRepository
import com.cocro.domain.common.CocroResult
import com.cocro.domain.grid.error.GridError
import org.springframework.stereotype.Service

@Service
class GetMyGridsUseCase(
    private val currentUserProvider: CurrentUserProvider,
    private val gridRepository: GridRepository,
) {
    fun execute(): CocroResult<List<GridSummaryDto>, GridError> {
        val user = currentUserProvider.currentUserOrNull()!!
        val grids = gridRepository.findByAuthor(user.userId)
        return CocroResult.Success(grids.map { it.toSummaryDto() })
    }
}
