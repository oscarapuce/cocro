package com.cocro.integration

import com.cocro.application.auth.dto.AuthSuccess
import com.cocro.application.session.dto.CreateSessionDto
import com.cocro.application.session.dto.JoinSessionDto
import com.cocro.application.session.dto.LeaveSessionDto
import com.cocro.application.session.dto.SessionStateDto
import com.redis.testcontainers.RedisContainer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.http.HttpStatus
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.MongoDBContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

/**
 * Integration tests for the full session lifecycle.
 *
 * Requires Docker. Starts real MongoDB + Redis containers via Testcontainers.
 * Exercises the full stack: HTTP -> Controller -> UseCase -> Domain -> MongoDB/Redis.
 */
@SpringBootTest(webEnvironment = RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
class SessionLifecycleIT : ITBase() {

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

    // -------------------------------------------------------------------------
    // Full lifecycle: create -> join -> update grid -> resync
    // -------------------------------------------------------------------------

    @Test
    fun `full session lifecycle -- create, join, update grid, resync`() {
        val creatorToken = tokenFor()
        val joinerToken = tokenFor()

        // --- SEED GRID ---
        val gridId = createTestGrid(creatorToken)

        // --- CREATE ---
        val shareCode = createSession(gridId, creatorToken)

        // --- JOIN ---
        val joinResp = post("/api/sessions/join", JoinSessionDto(shareCode = shareCode), joinerToken, Map::class.java)
        assertThat(joinResp.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(joinResp.body!!["participantCount"]).isEqualTo(1)

        // --- RESYNC (grid updates happen via STOMP -- see SessionWebSocketIT) ---
        val stateResp = get("/api/sessions/$shareCode/state", joinerToken, SessionStateDto::class.java)
        assertThat(stateResp.statusCode).isEqualTo(HttpStatus.OK)
        val state = stateResp.body!!
        assertThat(state.revision).isEqualTo(0L)
        assertThat(state.cells).isEmpty()
    }

    @Test
    fun `joining a full session returns 409 SessionFull`() {
        val creatorToken = tokenFor()
        val gridId = createTestGrid(creatorToken)
        val shareCode = createSession(gridId, creatorToken)

        repeat(4) {
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
    fun `leave then rejoin is allowed (LEFT participant flips back to JOINED)`() {
        val creatorToken = tokenFor()
        val joinerToken = tokenFor()
        val gridId = createTestGrid(creatorToken)
        val shareCode = createSession(gridId, creatorToken)
        post("/api/sessions/join", JoinSessionDto(shareCode = shareCode), joinerToken, Any::class.java)
        post("/api/sessions/leave", LeaveSessionDto(shareCode = shareCode), joinerToken, Any::class.java)

        // LEFT participants may rejoin -- their entry is flipped in-place, no duplicate slot added
        val rejoin = post("/api/sessions/join", JoinSessionDto(shareCode = shareCode), joinerToken, Map::class.java)
        assertThat(rejoin.statusCode).isEqualTo(HttpStatus.OK)
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
        val gridId = createTestGrid(creatorToken)
        val shareCode = createSession(gridId, creatorToken)

        // Guest joins with the token from /auth/guest
        val joinResp = post("/api/sessions/join", JoinSessionDto(shareCode = shareCode), guest.token, Map::class.java)
        assertThat(joinResp.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(joinResp.body!!["participantCount"]).isEqualTo(1)
    }

    // -------------------------------------------------------------------------
    // State endpoint -- authorization errors
    // -------------------------------------------------------------------------

    @Test
    fun `get state returns 403 when user is not a participant`() {
        val creatorToken = tokenFor()
        val outsiderToken = tokenFor()
        val gridId = createTestGrid(creatorToken)
        val shareCode = createSession(gridId, creatorToken)

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
    // Check grid endpoint -- error scenarios
    // -------------------------------------------------------------------------

    @Test
    fun `check grid returns 403 for non-existent session`() {
        val token = tokenFor()
        val resp = post("/api/sessions/ZZZZ/check", emptyMap<String, String>(), token, Any::class.java)
        assertThat(resp.statusCode).isEqualTo(HttpStatus.FORBIDDEN)
    }

    @Test
    fun `check grid returns result for valid session`() {
        val creatorToken = tokenFor()
        val gridId = createTestGrid(creatorToken)
        val shareCode = createSession(gridId, creatorToken)
        post("/api/sessions/join", JoinSessionDto(shareCode = shareCode), creatorToken, Any::class.java)

        val resp = post("/api/sessions/$shareCode/check", emptyMap<String, String>(), creatorToken, Map::class.java)
        assertThat(resp.statusCode).isEqualTo(HttpStatus.OK)
        val body = resp.body!! as Map<String, Any?>
        assertThat(body).containsKey("isComplete")
        assertThat(body).containsKey("isCorrect")
    }
}
