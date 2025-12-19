package com.cocro.presentation.grid.rest

import com.cocro.application.grid.dto.SubmitGridDto
import com.cocro.application.grid.usecase.SubmitGridUseCase
import com.cocro.kernel.common.Result
import com.cocro.presentation.common.error.toApiError
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/grids")
class GridController(
    private val submitGridUseCase: SubmitGridUseCase
) {

    @PostMapping("/submit")
    fun submitGrid(@RequestBody dto: SubmitGridDto): ResponseEntity<Any> {

        return when (val result = submitGridUseCase.execute(dto)) {
            is Result.Success -> ResponseEntity
                .status(HttpStatus.CREATED)
                .body(mapOf("gridId" to result.value.value))

            is Result.Error -> {
                val errors = result.errors.map { it.toApiError() }
                val status = result.errors.maxOf { it.errorCode.httpCode }

                ResponseEntity
                    .status(status)
                    .body(mapOf("errors" to errors))
            }
        }
    }

}
