package com.cocro.application.auth.dto

data class RegisterUserCommand(
    val username: String,
    val password: String,
    val email: String?,
)
