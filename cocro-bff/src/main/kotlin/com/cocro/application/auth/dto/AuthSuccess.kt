package com.cocro.application.auth.dto

data class AuthSuccess(
    val userId: String,
    val username: String,
    val roles: Set<String>,
    val token: String,
)
