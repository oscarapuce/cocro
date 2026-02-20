package com.cocro.application.grid.service

import com.cocro.application.common.service.AlphaNumCodeGenerator
import com.cocro.application.grid.port.GridRepository
import com.cocro.kernel.grid.model.valueobject.GridShareCode
import com.cocro.kernel.grid.rule.GridShareCodeRule
import org.springframework.stereotype.Service

@Service
class GridIdGenerator(
    private val gridRepository: GridRepository,
) : AlphaNumCodeGenerator<GridShareCode>(size = GridShareCodeRule.GRID_SHARE_CODE_SIZE) {
    override fun wrap(raw: String): GridShareCode = GridShareCode(raw)

    override fun exists(candidate: GridShareCode): Boolean = gridRepository.existsByShortId(candidate)
}
