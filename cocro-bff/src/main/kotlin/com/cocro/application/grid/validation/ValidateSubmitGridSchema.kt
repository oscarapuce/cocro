package com.cocro.application.grid.validation

import com.cocro.application.grid.dto.SubmitGridDto
import com.cocro.application.grid.validation.dsl.engine.validateGrid
import com.cocro.kernel.grid.enums.CellType
import com.cocro.kernel.grid.error.GridError

internal fun validateSubmitGrid(dto: SubmitGridDto): List<GridError> =
    validateGrid(dto) {
        title { required() }

        author { optionalSafeString() }
        reference { optionalSafeString() }
        description { optionalSafeString() }

        size {
            width { apply() }
            height { apply() }
            cellCountMatches()
        }

        cells {
            each {
                whenType(CellType.LETTER) {
                    letter { singleUppercase() }
                }
                whenType(CellType.CLUE_SINGLE) {
                    clues { exactly(1) }
                }
                whenType(CellType.CLUE_DOUBLE) {
                    clues {
                        exactly(2)
                        directionsMustDiffer()
                    }
                }
            }
        }
    }
