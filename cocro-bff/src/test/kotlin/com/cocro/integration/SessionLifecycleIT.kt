package com.cocro.integration

import com.cocro.application.session.dto.CreateSessionDto
import com.cocro.application.session.dto.JoinSessionDto
import com.cocro.application.session.dto.LeaveSessionDto
import com.cocro.application.session.dto.SessionCreationSuccess
import com.cocro.application.session.dto.SessionJoinSuccess
import com.cocro.application.session.dto.SessionStateDto
import com.cocro.application.session.dto.StartSessionDto
import com.cocro.application.session.dto.UpdateSessionGridDto
import com.cocro.infrastructure.security.jwt.JwtTokenIssuer
import com.cocro.kernel.auth.enum.Role
import com.cocro.kernel.auth.model.valueobject.UserId
import com.redis.testcontainers.RedisServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Disabled
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
@Disabled("Integration tests — require Docker")
@SpringBootTest(webEnvironment = RANDOM_PORT)
@Testcontainers
class SessionLifecycleIT {

    companion object {
        @Container
        @ServiceConnection
        val mongo = MongoDBContainer("mongo:7")

        @Container
        val redis = RedisServer()

        @JvmStatic
        @DynamicPropertySource
        fun redisProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.data.redis.host", redis::getHost)
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
    fun `full session lifecycle — create, join, start, update grid, resync`() {
        val creatorToken = tokenFor()
        val joinerToken = tokenFor()

        // --- CREATE ---
        val createResp = post("/api/sessions", CreateSessionDto(gridId = "GRID01"), creatorToken, SessionCreationSuccess::class.java)
        assertThat(createResp.statusCode).isEqualTo(HttpStatus.OK)
        val shareCode = createResp.body!!.shareCode

        // --- JOIN ---
        val joinResp = post("/api/sessions/join", JoinSessionDto(shareCode = shareCode), joinerToken, SessionJoinSuccess::class.java)
        assertThat(joinResp.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(joinResp.body!!.participantCount).isEqualTo(2)

        // --- START ---
        val startResp = post("/api/sessions/start", StartSessionDto(shareCode = shareCode), creatorToken, Any::class.java)
        assertThat(startResp.statusCode).isEqualTo(HttpStatus.OK)

        // --- UPDATE GRID (REST fallback, primary path is WebSocket) ---
        // TODO: switch to STOMP once WebSocket integration is wired (see SessionWebSocketIT below)
        val updateResp = post(
            "/api/sessions/grid",
            UpdateSessionGridDto(shareCode = shareCode, posX = 0, posY = 0, commandType = "PLACE_LETTER", letter = 'A'),
            creatorToken,
            Any::class.java,
        )
        assertThat(updateResp.statusCode).isEqualTo(HttpStatus.OK)

        // --- RESYNC ---
        val stateResp = get("/api/sessions/$shareCode/state", joinerToken, SessionStateDto::class.java)
        assertThat(stateResp.statusCode).isEqualTo(HttpStatus.OK)
        val state = stateResp.body!!
        assertThat(state.revision).isEqualTo(1L)
        assertThat(state.cells).anyMatch { it.x == 0 && it.y == 0 && it.letter == 'A' }
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
        assertThat(overflowResp.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
    }

    @Test
    fun `starting a session in PLAYING state returns 400 InvalidStatusForAction`() {
        val creatorToken = tokenFor()
        val shareCode = post("/api/sessions", CreateSessionDto(gridId = "GRID01"), creatorToken, SessionCreationSuccess::class.java)
            .body!!.shareCode
        post("/api/sessions/start", StartSessionDto(shareCode = shareCode), creatorToken, Any::class.java)

        val secondStart = post("/api/sessions/start", StartSessionDto(shareCode = shareCode), creatorToken, Any::class.java)
        assertThat(secondStart.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
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
        assertThat(rejoin.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
    }
}

// =============================================================================
// WebSocket integration — sketch
// =============================================================================
//
// To test STOMP in integration:
//
//  1. Use WebSocketStompClient + StandardWebSocketClient from Spring
//  2. Connect with JWT in the "Authorization" header (passed as STOMP CONNECT header)
//  3. Subscribe to /user/queue/session → receive SessionWelcome
//  4. Subscribe to /topic/session/{shareCode} → receive broadcasts
//  5. Send to /app/session/{shareCode}/grid → triggers grid updates
//
// Example skeleton:
//
// @Disabled
// @SpringBootTest(webEnvironment = RANDOM_PORT)
// @Testcontainers
// class SessionWebSocketIT {
//     // ... same container setup as SessionLifecycleIT
//
//     @LocalServerPort var port: Int = 0
//
//     @Test
//     fun `grid update via STOMP broadcasts GridUpdated to all subscribers`() {
//         val token = tokenFor()
//         val client = WebSocketStompClient(StandardWebSocketClient())
//         client.messageConverter = MappingJackson2MessageConverter()
//
//         val stompSession = client
//             .connectAsync("ws://localhost:$port/ws", StompSessionHandlerAdapter()) { headers ->
//                 headers.add("Authorization", "Bearer $token")
//                 headers.add("shareCode", shareCode)
//             }
//             .get(5, TimeUnit.SECONDS)
//
//         val received = LinkedBlockingQueue<SessionEvent>()
//         stompSession.subscribe("/topic/session/$shareCode") { payload ->
//             received.offer(objectMapper.readValue(payload, SessionEvent::class.java))
//         }
//
//         stompSession.send("/app/session/$shareCode/grid", GridUpdatePayload(0, 0, "PLACE_LETTER", 'A'))
//
//         val event = received.poll(3, TimeUnit.SECONDS)
//         assertThat(event).isInstanceOf(SessionEvent.GridUpdated::class.java)
//         assertThat((event as SessionEvent.GridUpdated).letter).isEqualTo('A')
//     }
// }
//
// =============================================================================
// Concurrency / CAS — sketch
// =============================================================================
//
// To test optimistic locking under concurrent updates:
//
// @Test
// fun `concurrent grid updates — only one wins per revision`() {
//     // ... setup session in PLAYING state
//
//     val latch = CountDownLatch(2)
//     val results = ConcurrentLinkedQueue<Int>()
//
//     repeat(2) { i ->
//         thread {
//             latch.countDown()
//             latch.await()
//             val resp = post("/api/sessions/grid", UpdateSessionGridDto(..., letter = 'A' + i), token, Any::class.java)
//             results += resp.statusCode.value()
//         }
//     }
//
//     // Exactly one 200 and one 409 (or both 200 if Redis CAS serialises correctly)
//     // This test validates that no silent data loss occurs
// }
