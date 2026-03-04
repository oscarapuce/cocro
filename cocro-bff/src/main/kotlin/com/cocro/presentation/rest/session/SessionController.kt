package com.cocro.presentation.rest.session

import com.cocro.application.session.dto.CreateSessionDto
import com.cocro.application.session.dto.JoinSessionDto
import com.cocro.application.session.dto.LeaveSessionDto
import com.cocro.application.session.dto.StartSessionDto
import com.cocro.application.session.usecase.CreateSessionUseCase
import com.cocro.application.session.usecase.JoinSessionUseCase
import com.cocro.application.session.usecase.LeaveSessionUseCase
import com.cocro.application.session.usecase.StartSessionUseCase
import com.cocro.presentation.rest.error.toResponseEntity
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/sessions")
class SessionController(
    private val createSessionUseCase: CreateSessionUseCase,
    private val joinSessionUseCase: JoinSessionUseCase,
    private val leaveSessionUseCase: LeaveSessionUseCase,
    private val startSessionUseCase: StartSessionUseCase,
) {
    @PostMapping
    @PreAuthorize("hasAnyRole('PLAYER', 'ADMIN')")
    fun createSession(
        @RequestBody dto: CreateSessionDto,
    ): ResponseEntity<*> =
        createSessionUseCase
            .execute(dto)
            .toResponseEntity(HttpStatus.CREATED)

    @PostMapping("/join")
    @PreAuthorize("hasAnyRole('PLAYER', 'ADMIN')")
    fun joinSession(
        @RequestBody dto: JoinSessionDto,
    ): ResponseEntity<*> =
        joinSessionUseCase
            .execute(dto)
            .toResponseEntity(HttpStatus.OK)

    @PostMapping("/leave")
    @PreAuthorize("hasAnyRole('PLAYER', 'ADMIN')")
    fun leaveSession(
        @RequestBody dto: LeaveSessionDto,
    ): ResponseEntity<*> =
        leaveSessionUseCase
            .execute(dto)
            .toResponseEntity(HttpStatus.OK)

    @PostMapping("/start")
    @PreAuthorize("hasAnyRole('PLAYER', 'ADMIN')")
    fun startSession(
        @RequestBody dto: StartSessionDto,
    ): ResponseEntity<*> =
        startSessionUseCase
            .execute(dto)
            .toResponseEntity(HttpStatus.OK)
}
