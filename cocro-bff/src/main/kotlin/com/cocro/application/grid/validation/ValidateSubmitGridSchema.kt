package com.cocro.application.grid.validation

import com.cocro.application.grid.dto.SubmitGridDto
import com.cocro.application.grid.validation.dsl.engine.validateGrid
import com.cocro.domain.grid.enums.CellType
import com.cocro.domain.grid.error.GridError

internal fun validateSubmitGrid(dto: SubmitGridDto): List<GridError> =
    validateGrid(dto) {
        title { required() }

        reference { optional() }
        description { optional() }

        size {
            required()
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

        globalClue(dto.globalClueLabel, dto.globalClueWordLengths, dto.cells)
    }
