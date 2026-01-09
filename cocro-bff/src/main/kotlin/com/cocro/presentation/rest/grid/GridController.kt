package com.cocro.presentation.rest.grid

import com.cocro.application.grid.dto.PatchGridDto
import com.cocro.application.grid.dto.SubmitGridDto
import com.cocro.application.grid.usecase.PatchGridUseCase
import com.cocro.application.grid.usecase.SubmitGridUseCase
import com.cocro.presentation.rest.error.toResponseEntity
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/grids")
class GridController(
    private val submitGridUseCase: SubmitGridUseCase,
    private val patchGridUseCase: PatchGridUseCase,
) {
    @PostMapping
    @PreAuthorize("hasAnyRole('PLAYER', 'ADMIN')")
    fun submitGrid(
        @RequestBody dto: SubmitGridDto,
    ): ResponseEntity<*> =
        submitGridUseCase
            .execute(dto)
            .toResponseEntity(HttpStatus.CREATED)

    @PatchMapping
    @PreAuthorize("hasAnyRole('PLAYER', 'ADMIN')")
    fun updateGrid(
        @RequestBody dto: PatchGridDto,
    ): ResponseEntity<*> =
        patchGridUseCase
            .execute(dto)
            .toResponseEntity(HttpStatus.OK)
}
