package com.cocro.integration

import com.cocro.application.auth.dto.AuthSuccess
import com.cocro.application.grid.dto.CellDto
import com.cocro.application.grid.dto.ClueDto
import com.cocro.application.grid.dto.SubmitGridDto
import com.cocro.application.session.dto.CreateSessionDto
import com.cocro.application.session.dto.JoinSessionDto
import com.cocro.application.session.dto.LeaveSessionDto
import com.cocro.application.session.dto.SessionStateDto
import com.cocro.infrastructure.security.jwt.JwtTokenIssuer
import com.cocro.domain.auth.enum.Role
import com.cocro.domain.auth.model.valueobject.UserId
import com.cocro.domain.grid.enums.CellType
import com.cocro.domain.grid.enums.ClueDirection
import com.cocro.domain.grid.enums.SeparatorType
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
class SessionLifecycleIT {

    companion object {
        @Container
        @ServiceConnection
        val mongo = MongoDBContainer("mongo:7")

        @Container
        val redis = RedisContainer("redis:7-alpine")

        /** Incremented per createTestGrid call to ensure unique letter hashes. */
        private var gridCounter = 0

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

    /**
     * Creates a valid 5x5 grid via the API and returns its shortId (gridId).
     * Min grid size is 5x5 per GridWidthRule/GridHeightRule.
     *
     * GridShareCode is a @JvmInline value class; Jackson serializes it as a plain string.
     */
    private fun createTestGrid(token: String): String {
        gridCounter++
        val w = 5
        val h = 5
        val cells = (0 until h).flatMap { y ->
            (0 until w).map { x ->
                if (x == 0 && y == 0) {
                    CellDto(
                        x = x, y = y, type = CellType.CLUE_SINGLE,
                        letter = null, separator = null, number = null,
                        clues = listOf(ClueDto(direction = ClueDirection.RIGHT, text = "Test clue")),
                    )
                } else {
                    // Vary the letter per gridCounter to avoid duplicate hash detection
                    val letter = ('A' + ((x + y + gridCounter) % 26)).toString()
                    CellDto(
                        x = x, y = y, type = CellType.LETTER,
                        letter = letter, separator = SeparatorType.NONE, number = null,
                        clues = null,
                    )
                }
            }
        }
        val gridDto = SubmitGridDto(
            title = "Test Grid $gridCounter",
            width = w,
            height = h,
            difficulty = "NONE",
            reference = null,
            description = null,
            cells = cells,
        )
        val resp = post("/api/grids", gridDto, token, String::class.java)
        assertThat(resp.statusCode).isEqualTo(HttpStatus.CREATED)
        val body = resp.body!!
        // Value class might serialize as plain string "ABCDEF" or as JSON {"value":"ABCDEF"}
        return if (body.startsWith("{")) {
            body.substringAfter("\"value\":\"").substringBefore("\"")
        } else {
            body.trim('"')
        }
    }

    /**
     * Creates a session via POST /api/sessions and returns the shareCode.
     */
    @Suppress("UNCHECKED_CAST")
    private fun createSession(gridId: String, token: String): String {
        val resp = post("/api/sessions", CreateSessionDto(gridId = gridId), token, Map::class.java)
        assertThat(resp.statusCode).isEqualTo(HttpStatus.CREATED)
        return resp.body!!["shareCode"] as String
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
