package com.cocro.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(
    val username: String,
    val password: String,
)

@Serializable
data class AuthResponse(
    val userId: String,
    val username: String,
    val roles: Set<String>,
    val token: String,
)

@Serializable
data class RegisterRequest(
    val username: String,
    val password: String,
    val email: String? = null,
)

@Serializable
data class GuestRequest(
    val hint: String? = null,
)
