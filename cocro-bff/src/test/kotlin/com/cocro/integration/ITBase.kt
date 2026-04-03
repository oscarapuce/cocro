package com.cocro.integration

import com.cocro.application.grid.dto.CellDto
import com.cocro.application.grid.dto.ClueDto
import com.cocro.application.grid.dto.SubmitGridDto
import com.cocro.application.session.dto.CreateSessionDto
import com.cocro.infrastructure.security.jwt.JwtTokenIssuer
import com.cocro.domain.auth.enum.Role
import com.cocro.domain.auth.model.valueobject.UserId
import com.cocro.domain.grid.enums.CellType
import com.cocro.domain.grid.enums.ClueDirection
import com.cocro.domain.grid.enums.SeparatorType
import com.redis.testcontainers.RedisContainer
import org.assertj.core.api.Assertions.assertThat
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.testcontainers.containers.MongoDBContainer
import org.testcontainers.junit.jupiter.Container
import java.util.concurrent.atomic.AtomicInteger

/**
 * Shared base for all BFF integration tests.
 *
 * Provides:
 * - Testcontainers wiring (MongoDB 7 + Redis 7)
 * - Token generation helpers
 * - HTTP helper methods (get, post, patch, delete)
 * - Grid and session creation helpers that deduplicate the boilerplate
 *
 * Each concrete IT class still declares its own @SpringBootTest + @Testcontainers
 * and references the companion containers below via @ServiceConnection or
 * @DynamicPropertySource (Redis needs dynamic port registration because
 * ServiceConnection does not support Redis password-less configuration out of the box).
 */
abstract class ITBase {

    companion object {
        /**
         * Shared container declarations referenced by concrete IT subclasses.
         * Each concrete class must declare @Container on these in its own companion
         * OR use the static companion references here.
         *
         * We keep them here so subclasses can share the container definition
         * but still opt-in to @ServiceConnection / @DynamicPropertySource themselves.
         */

        /**
         * Global counter to avoid duplicate letter-hash collisions across grids.
         * AtomicInteger so tests running in parallel threads don't clash.
         */
        private val gridCounter = AtomicInteger(0)

        fun nextGridCounter(): Int = gridCounter.incrementAndGet()

        /**
         * Builds a valid 5x5 SubmitGridDto ready to POST to /api/grids.
         * Each call uses a fresh [counter] so letter hashes never collide.
         */
        fun buildSubmitGridDto(counter: Int, title: String = "Test Grid $counter"): SubmitGridDto {
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
                        val letter = ('A' + ((x + y + counter) % 26)).toString()
                        CellDto(
                            x = x, y = y, type = CellType.LETTER,
                            letter = letter, separator = SeparatorType.NONE, number = null,
                            clues = null,
                        )
                    }
                }
            }
            return SubmitGridDto(
                title = title,
                width = w,
                height = h,
                difficulty = "NONE",
                reference = null,
                description = null,
                cells = cells,
            )
        }
    }

    @Autowired lateinit var restTemplate: TestRestTemplate
    @Autowired lateinit var tokenIssuer: JwtTokenIssuer

    // -------------------------------------------------------------------------
    // Token helpers
    // -------------------------------------------------------------------------

    fun tokenFor(
        userId: UserId = UserId.new(),
        username: String = "TestUser",
        roles: Set<Role> = setOf(Role.PLAYER),
    ): String = tokenIssuer.issue(userId, username, roles)

    fun headersFor(token: String) = HttpHeaders().apply {
        setBearerAuth(token)
        set("Content-Type", "application/json")
    }

    // -------------------------------------------------------------------------
    // HTTP helpers
    // -------------------------------------------------------------------------

    fun <T> post(path: String, body: Any, token: String, responseType: Class<T>) =
        restTemplate.exchange(path, HttpMethod.POST, HttpEntity(body, headersFor(token)), responseType)

    fun <T> get(path: String, token: String, responseType: Class<T>) =
        restTemplate.exchange(path, HttpMethod.GET, HttpEntity<Unit>(headersFor(token)), responseType)

    fun <T> patch(path: String, body: Any, token: String, responseType: Class<T>) =
        restTemplate.exchange(path, HttpMethod.PATCH, HttpEntity(body, headersFor(token)), responseType)

    fun <T> delete(path: String, token: String, responseType: Class<T>) =
        restTemplate.exchange(path, HttpMethod.DELETE, HttpEntity<Unit>(headersFor(token)), responseType)

    // -------------------------------------------------------------------------
    // Domain object creation helpers
    // -------------------------------------------------------------------------

    /**
     * Creates a valid grid via POST /api/grids and returns the gridId (shortId).
     * Uses an atomic counter to ensure unique letter hashes.
     */
    fun createTestGrid(token: String, title: String? = null): String {
        val counter = nextGridCounter()
        val dto = buildSubmitGridDto(counter, title ?: "Test Grid $counter")
        val resp = post("/api/grids", dto, token, String::class.java)
        assertThat(resp.statusCode).isEqualTo(HttpStatus.CREATED)
        val body = resp.body!!
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
    fun createSession(gridId: String, token: String): String {
        val resp = post("/api/sessions", CreateSessionDto(gridId = gridId), token, Map::class.java)
        assertThat(resp.statusCode).isEqualTo(HttpStatus.CREATED)
        return resp.body!!["shareCode"] as String
    }

    /**
     * Registers a new user via POST /auth/register and returns the JWT token.
     */
    @Suppress("UNCHECKED_CAST")
    fun registerAndLogin(username: String, password: String = "Password1!"): String {
        val body = mapOf("username" to username, "password" to password, "email" to null)
        val resp = restTemplate.postForEntity("/auth/register", body, Map::class.java)
        assertThat(resp.statusCode).isIn(HttpStatus.CREATED, HttpStatus.OK)
        return resp.body!!["token"] as String
    }
}
