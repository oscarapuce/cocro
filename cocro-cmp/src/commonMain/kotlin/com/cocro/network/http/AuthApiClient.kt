package com.cocro.network.http

import com.cocro.network.dto.AuthResponse
import com.cocro.network.dto.GuestRequest
import com.cocro.network.dto.LoginRequest
import com.cocro.network.dto.RegisterRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

class AuthApiClient(
    private val httpClient: HttpClient,
    private val baseUrl: String,
) {
    suspend fun login(username: String, password: String): AuthResponse =
        httpClient
            .post("$baseUrl/auth/login") {
                contentType(ContentType.Application.Json)
                setBody(LoginRequest(username, password))
            }
            .body()

    suspend fun register(username: String, password: String, email: String? = null): AuthResponse =
        httpClient
            .post("$baseUrl/auth/register") {
                contentType(ContentType.Application.Json)
                setBody(RegisterRequest(username, password, email))
            }
            .body()

    suspend fun guest(): AuthResponse =
        httpClient
            .post("$baseUrl/auth/guest") {
                contentType(ContentType.Application.Json)
                setBody(GuestRequest())
            }
            .body()
}
