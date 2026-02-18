package com.cocro.application.auth.dto

data class LoginUserCommandDto(
    val username: String,
    val password: String,
)

data class RegisterUserCommandDto(
    val username: String,
    val password: String,
    val email: String?,
)
