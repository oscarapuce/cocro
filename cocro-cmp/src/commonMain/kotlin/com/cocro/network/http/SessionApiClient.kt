package com.cocro.network.http

import com.cocro.network.dto.CreateSessionRequest
import com.cocro.network.dto.CreateSessionResponse
import com.cocro.network.dto.JoinSessionRequest
import com.cocro.network.dto.JoinSessionResponse
import com.cocro.network.dto.LeaveSessionRequest
import com.cocro.network.dto.LeaveSessionResponse
import com.cocro.network.dto.SessionStateResponse
import com.cocro.network.dto.StartSessionRequest
import com.cocro.network.dto.StartSessionResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

class SessionApiClient(
    private val httpClient: HttpClient,
    private val baseUrl: String,
) {
    suspend fun create(token: String, gridId: String): CreateSessionResponse =
        httpClient
            .post("$baseUrl/api/sessions") {
                bearerAuth(token)
                contentType(ContentType.Application.Json)
                setBody(CreateSessionRequest(gridId))
            }
            .body()

    suspend fun join(token: String, shareCode: String): JoinSessionResponse =
        httpClient
            .post("$baseUrl/api/sessions/join") {
                bearerAuth(token)
                contentType(ContentType.Application.Json)
                setBody(JoinSessionRequest(shareCode))
            }
            .body()

    suspend fun leave(token: String, shareCode: String): LeaveSessionResponse =
        httpClient
            .post("$baseUrl/api/sessions/leave") {
                bearerAuth(token)
                contentType(ContentType.Application.Json)
                setBody(LeaveSessionRequest(shareCode))
            }
            .body()

    suspend fun start(token: String, shareCode: String): StartSessionResponse =
        httpClient
            .post("$baseUrl/api/sessions/start") {
                bearerAuth(token)
                contentType(ContentType.Application.Json)
                setBody(StartSessionRequest(shareCode))
            }
            .body()

    suspend fun getState(token: String, shareCode: String): SessionStateResponse =
        httpClient
            .get("$baseUrl/api/sessions/$shareCode/state") {
                bearerAuth(token)
            }
            .body()
}
