package com.cocro.application.grid.validation

import com.cocro.application.grid.dto.PatchGridDto
import com.cocro.application.grid.validation.dsl.engine.validateGrid
import com.cocro.domain.grid.enums.CellType
import com.cocro.domain.grid.error.GridError

internal fun validatePatchGrid(dto: PatchGridDto): List<GridError> =
    validateGrid(dto) {
        gridId { required() }

        title { optional() }

        reference { optional() }
        description { optional() }

        size {
            optional()
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
