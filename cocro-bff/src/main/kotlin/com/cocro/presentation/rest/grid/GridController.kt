package com.cocro.presentation.rest.grid

import com.cocro.application.grid.dto.SubmitGridDto
import com.cocro.application.grid.usecase.SubmitGridUseCase
import com.cocro.presentation.rest.error.toResponseEntity
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/grids")
class GridController(
    private val submitGridUseCase: SubmitGridUseCase,
) {
    @PostMapping("/submit")
    @PreAuthorize("hasRole('PLAYER')")
    fun submitGrid(
        @RequestBody dto: SubmitGridDto,
    ): ResponseEntity<*> =
        submitGridUseCase
            .execute(dto)
            .toResponseEntity(HttpStatus.CREATED)
}
