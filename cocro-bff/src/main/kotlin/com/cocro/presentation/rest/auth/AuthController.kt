package com.cocro.presentation.rest.auth

import com.cocro.application.auth.dto.LoginUserCommand
import com.cocro.application.auth.dto.RegisterUserCommand
import com.cocro.application.auth.usecase.LoginUserUseCase
import com.cocro.application.auth.usecase.RegisterUserUseCase
import com.cocro.presentation.rest.error.toResponseEntity
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/auth")
class AuthController(
    private val registerUserUseCase: RegisterUserUseCase,
    private val loginUserUseCase: LoginUserUseCase,
) {
    @PostMapping("/register")
    fun register(
        @RequestBody request: RegisterUserCommand,
    ): ResponseEntity<*> =
        registerUserUseCase
            .execute(request)
            .toResponseEntity(HttpStatus.CREATED)

    @PostMapping("/login")
    fun login(
        @RequestBody request: LoginUserCommand,
    ): ResponseEntity<*> =
        loginUserUseCase
            .execute(request)
            .toResponseEntity(HttpStatus.OK)
}
