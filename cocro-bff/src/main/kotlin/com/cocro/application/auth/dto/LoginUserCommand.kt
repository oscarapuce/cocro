package com.cocro.application.auth.dto

data class LoginUserCommand(
    val username: String,
    val password: String,
)
