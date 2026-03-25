package com.cocro.presentation.rest.session

import com.cocro.application.session.dto.CreateSessionDto
import com.cocro.application.session.dto.JoinSessionDto
import com.cocro.application.session.dto.LeaveSessionDto
import com.cocro.application.session.usecase.CheckGridUseCase
import com.cocro.application.session.usecase.CreateSessionUseCase
import com.cocro.application.session.usecase.GetSessionStateUseCase
import com.cocro.application.session.usecase.JoinSessionUseCase
import com.cocro.application.session.usecase.LeaveSessionUseCase
import com.cocro.presentation.rest.error.toResponseEntity
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
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
    private val getSessionStateUseCase: GetSessionStateUseCase,
    private val checkGridUseCase: CheckGridUseCase,
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
    @PreAuthorize("hasAnyRole('PLAYER', 'ADMIN', 'ANONYMOUS')")
    fun joinSession(
        @RequestBody dto: JoinSessionDto,
    ): ResponseEntity<*> =
        joinSessionUseCase
            .execute(dto)
            .toResponseEntity(HttpStatus.OK)

    @PostMapping("/leave")
    @PreAuthorize("hasAnyRole('PLAYER', 'ADMIN', 'ANONYMOUS')")
    fun leaveSession(
        @RequestBody dto: LeaveSessionDto,
    ): ResponseEntity<*> =
        leaveSessionUseCase
            .execute(dto)
            .toResponseEntity(HttpStatus.OK)

    /** Resync endpoint: returns the current grid state from cache or MongoDB. */
    @GetMapping("/{shareCode}/state")
    @PreAuthorize("hasAnyRole('PLAYER', 'ADMIN', 'ANONYMOUS')")
    fun getSessionState(
        @PathVariable shareCode: String,
    ): ResponseEntity<*> =
        getSessionStateUseCase
            .execute(shareCode)
            .toResponseEntity(HttpStatus.OK)

    /**
     * Validates the current session grid state against the reference grid in MongoDB.
     * Read-only — no state transition is triggered.
     * Returns [GridCheckSuccess] with isComplete, isCorrect, filledCount, totalCount.
     */
    @PostMapping("/{shareCode}/check")
    @PreAuthorize("hasAnyRole('PLAYER', 'ADMIN', 'ANONYMOUS')")
    fun checkGrid(
        @PathVariable shareCode: String,
    ): ResponseEntity<*> =
        checkGridUseCase
            .execute(shareCode)
            .toResponseEntity(HttpStatus.OK)
}
