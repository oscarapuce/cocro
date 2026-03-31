package com.cocro.integration

import com.cocro.application.grid.dto.CellDto
import com.cocro.application.grid.dto.ClueDto
import com.cocro.application.grid.dto.SubmitGridDto
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

@SpringBootTest(webEnvironment = RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
class GridControllerIT {

    companion object {
        @Container
        @ServiceConnection
        val mongo = MongoDBContainer("mongo:7")

        @Container
        val redis = RedisContainer("redis:7-alpine")

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
        tokenIssuer.issue(userId, "TestUser", roles)

    private fun headersFor(token: String) = HttpHeaders().apply {
        setBearerAuth(token)
        set("Content-Type", "application/json")
    }

    private fun <T> post(path: String, body: Any, token: String, responseType: Class<T>) =
        restTemplate.exchange(path, HttpMethod.POST, HttpEntity(body, headersFor(token)), responseType)

    private fun <T> get(path: String, token: String, responseType: Class<T>) =
        restTemplate.exchange(path, HttpMethod.GET, HttpEntity<Unit>(headersFor(token)), responseType)

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
        return if (body.startsWith("{")) {
            body.substringAfter("\"value\":\"").substringBefore("\"")
        } else {
            body.trim('"')
        }
    }

    // -------------------------------------------------------------------------
    // GET /api/grids/mine
    // -------------------------------------------------------------------------

    @Test
    fun `get mine should return 401 when not authenticated`() {
        val resp = restTemplate.getForEntity("/api/grids/mine", Any::class.java)
        assertThat(resp.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
    }

    @Test
    fun `get mine should return empty list when user has no grids`() {
        val token = tokenFor()

        val resp = get("/api/grids/mine", token, Array<Any>::class.java)

        assertThat(resp.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(resp.body!!).isEmpty()
    }

    @Test
    fun `get mine should return list of grids created by the authenticated user`() {
        val userId = UserId.new()
        val token = tokenFor(userId)
        val shortId = createTestGrid(token)

        val resp = get("/api/grids/mine", token, Array<Any>::class.java)

        assertThat(resp.statusCode).isEqualTo(HttpStatus.OK)
        val list = resp.body!!
        assertThat(list).isNotEmpty()
        @Suppress("UNCHECKED_CAST")
        val first = list[0] as Map<String, Any>
        assertThat(first["gridId"]).isEqualTo(shortId)
    }

    @Test
    fun `get mine should not return grids created by another user`() {
        val userA = UserId.new()
        val tokenA = tokenFor(userA)
        createTestGrid(tokenA)

        val userB = UserId.new()
        val tokenB = tokenFor(userB)

        val resp = get("/api/grids/mine", tokenB, Array<Any>::class.java)

        assertThat(resp.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(resp.body!!).isEmpty()
    }

    // -------------------------------------------------------------------------
    // GET /api/grids/{shortId}
    // -------------------------------------------------------------------------

    @Test
    fun `get grid should return full grid for valid shortId`() {
        val token = tokenFor()
        val shortId = createTestGrid(token)

        @Suppress("UNCHECKED_CAST")
        val resp = get("/api/grids/$shortId", token, Map::class.java)

        assertThat(resp.statusCode).isEqualTo(HttpStatus.OK)
        val body = resp.body!! as Map<String, Any>
        assertThat(body["gridId"]).isEqualTo(shortId)
    }

    @Test
    fun `get grid should return 400 for invalid shortId format`() {
        val token = tokenFor()

        val resp = get("/api/grids/INVAL!", token, Any::class.java)

        assertThat(resp.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
    }

    @Test
    fun `get grid should return 404 for non-existent shortId`() {
        val token = tokenFor()

        val resp = get("/api/grids/ZZZZZ1", token, Any::class.java)

        assertThat(resp.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
    }

    @Test
    fun `get grid should return 401 when not authenticated`() {
        val resp = restTemplate.getForEntity("/api/grids/ABCDE1", Any::class.java)
        assertThat(resp.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
    }
}
