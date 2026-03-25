package com.cocro.integration

import com.cocro.application.auth.dto.AuthSuccess
import com.cocro.application.session.dto.CreateSessionDto
import com.cocro.application.session.dto.JoinSessionDto
import com.cocro.application.session.dto.LeaveSessionDto
import com.cocro.application.session.dto.SessionCreationSuccess
import com.cocro.application.session.dto.SessionJoinSuccess
import com.cocro.application.session.dto.SessionStateDto
import com.cocro.infrastructure.security.jwt.JwtTokenIssuer
import com.cocro.kernel.auth.enum.Role
import com.cocro.kernel.auth.model.valueobject.UserId
import com.redis.testcontainers.RedisContainer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.MongoDBContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

/**
 * Integration tests for the full session lifecycle.
 *
 * Requires Docker. Starts real MongoDB + Redis containers via Testcontainers.
 * Exercises the full stack: HTTP → Controller → UseCase → Domain → MongoDB/Redis.
 *
 * Remove @Disabled once the test environment is validated (CI Docker available).
 */
@SpringBootTest(webEnvironment = RANDOM_PORT)
@Testcontainers
class SessionLifecycleIT {

    companion object {
        @Container
        @ServiceConnection
        val mongo = MongoDBContainer("mongo:7")

        @Container
        val redis = RedisContainer("redis:7-alpine")

        @JvmStatic
        @DynamicPropertySource
        fun redisProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.data.redis.host") { redis.host }
            registry.add("spring.data.redis.port") { redis.getMappedPort(6379) }
            registry.add("spring.data.redis.password") { "" }
        }
    }

    @Autowired lateinit var restTemplate: TestRestTemplate
    @Autowired lateinit var tokenIssuer: JwtTokenIssuer

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun tokenFor(userId: UserId = UserId.new(), roles: Set<Role> = setOf(Role.PLAYER)): String =
        tokenIssuer.issue(userId, roles)

    private fun headersFor(token: String) = HttpHeaders().apply {
        setBearerAuth(token)
        set("Content-Type", "application/json")
    }

    private fun <T> post(path: String, body: Any, token: String, responseType: Class<T>) =
        restTemplate.exchange(path, HttpMethod.POST, HttpEntity(body, headersFor(token)), responseType)

    private fun <T> get(path: String, token: String, responseType: Class<T>) =
        restTemplate.exchange(path, HttpMethod.GET, HttpEntity<Unit>(headersFor(token)), responseType)

    // -------------------------------------------------------------------------
    // Full lifecycle: create → join → start → update grid → resync
    // -------------------------------------------------------------------------

    @Test
    fun `full session lifecycle — create, join, update grid, resync`() {
        val creatorToken = tokenFor()
        val joinerToken = tokenFor()

        // --- CREATE ---
        val createResp = post("/api/sessions", CreateSessionDto(gridId = "GRID01"), creatorToken, SessionCreationSuccess::class.java)
        assertThat(createResp.statusCode).isEqualTo(HttpStatus.CREATED)
        val shareCode = createResp.body!!.shareCode

        // --- JOIN ---
        val joinResp = post("/api/sessions/join", JoinSessionDto(shareCode = shareCode), joinerToken, SessionJoinSuccess::class.java)
        assertThat(joinResp.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(joinResp.body!!.participantCount).isEqualTo(2)

        // --- RESYNC (grid updates happen via STOMP — see SessionWebSocketIT) ---
        val stateResp = get("/api/sessions/$shareCode/state", joinerToken, SessionStateDto::class.java)
        assertThat(stateResp.statusCode).isEqualTo(HttpStatus.OK)
        val state = stateResp.body!!
        assertThat(state.revision).isEqualTo(0L)
        assertThat(state.cells).isEmpty()
    }

    @Test
    fun `joining a full session returns 400 SessionFull`() {
        val creatorToken = tokenFor()
        val shareCode = post("/api/sessions", CreateSessionDto(gridId = "GRID01"), creatorToken, SessionCreationSuccess::class.java)
            .body!!.shareCode

        repeat(3) {
            post("/api/sessions/join", JoinSessionDto(shareCode = shareCode), tokenFor(), Any::class.java)
        }

        val overflowResp = post("/api/sessions/join", JoinSessionDto(shareCode = shareCode), tokenFor(), Any::class.java)
        assertThat(overflowResp.statusCode).isEqualTo(HttpStatus.CONFLICT)
    }

    @Test
    fun `unauthenticated request returns 401`() {
        val resp = restTemplate.postForEntity("/api/sessions", CreateSessionDto(gridId = "GRID01"), Any::class.java)
        assertThat(resp.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
    }

    @Test
    fun `leave then rejoin is rejected (AlreadyParticipant)`() {
        val creatorToken = tokenFor()
        val joinerToken = tokenFor()
        val shareCode = post("/api/sessions", CreateSessionDto(gridId = "GRID01"), creatorToken, SessionCreationSuccess::class.java)
            .body!!.shareCode
        post("/api/sessions/join", JoinSessionDto(shareCode = shareCode), joinerToken, Any::class.java)
        post("/api/sessions/leave", LeaveSessionDto(shareCode = shareCode), joinerToken, Any::class.java)

        // Left participants cannot rejoin (status LEFT — not re-joinable under current rules)
        // TODO: update this test if re-join is allowed in the future
        val rejoin = post("/api/sessions/join", JoinSessionDto(shareCode = shareCode), joinerToken, Any::class.java)
        assertThat(rejoin.statusCode).isEqualTo(HttpStatus.CONFLICT)
    }

    // -------------------------------------------------------------------------
    // Guest login + session participation
    // -------------------------------------------------------------------------

    @Test
    fun `guest can login and join a session`() {
        // Guest login (no auth required)
        val guestResp = restTemplate.postForEntity("/auth/guest", null, AuthSuccess::class.java)
        assertThat(guestResp.statusCode).isEqualTo(HttpStatus.OK)
        val guest = guestResp.body!!
        assertThat(guest.token).isNotBlank()
        assertThat(guest.roles).contains("ANONYMOUS")
        assertThat(guest.username).isNotBlank()

        // Creator creates a session
        val creatorToken = tokenFor()
        val shareCode = post("/api/sessions", CreateSessionDto(gridId = "GRID01"), creatorToken, SessionCreationSuccess::class.java)
            .body!!.shareCode

        // Guest joins with the token from /auth/guest
        val joinResp = post("/api/sessions/join", JoinSessionDto(shareCode = shareCode), guest.token, SessionJoinSuccess::class.java)
        assertThat(joinResp.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(joinResp.body!!.participantCount).isEqualTo(2)
    }

    // -------------------------------------------------------------------------
    // State endpoint — authorization errors
    // -------------------------------------------------------------------------

    @Test
    fun `get state returns 403 when user is not a participant`() {
        val creatorToken = tokenFor()
        val outsiderToken = tokenFor()
        val shareCode = post("/api/sessions", CreateSessionDto(gridId = "GRID01"), creatorToken, SessionCreationSuccess::class.java)
            .body!!.shareCode

        val stateResp = get("/api/sessions/$shareCode/state", outsiderToken, Any::class.java)
        assertThat(stateResp.statusCode).isEqualTo(HttpStatus.FORBIDDEN)
    }

    @Test
    fun `get state returns 403 for non-existent session`() {
        val token = tokenFor()
        val stateResp = get("/api/sessions/ZZZZ/state", token, Any::class.java)
        assertThat(stateResp.statusCode).isEqualTo(HttpStatus.FORBIDDEN)
    }

    // -------------------------------------------------------------------------
    // Check grid endpoint — error scenarios
    // -------------------------------------------------------------------------

    @Test
    fun `check grid returns 403 for non-existent session`() {
        val token = tokenFor()
        val resp = post("/api/sessions/ZZZZ/check", emptyMap<String, String>(), token, Any::class.java)
        assertThat(resp.statusCode).isEqualTo(HttpStatus.FORBIDDEN)
    }

    @Test
    fun `check grid returns 404 when reference grid not found`() {
        // Create a session (gridId GRID01 doesn't exist in DB)
        val creatorToken = tokenFor()
        val shareCode = post("/api/sessions", CreateSessionDto(gridId = "GRID01"), creatorToken, SessionCreationSuccess::class.java)
            .body!!.shareCode

        val resp = post("/api/sessions/$shareCode/check", emptyMap<String, String>(), creatorToken, Any::class.java)
        assertThat(resp.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
    }
}

