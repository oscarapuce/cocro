package com.cocro.integration

import com.cocro.application.session.dto.CreateSessionDto
import com.cocro.application.session.dto.JoinSessionDto
import com.cocro.application.session.dto.SessionCreationSuccess
import com.cocro.application.session.dto.StartSessionDto
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
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.messaging.converter.MappingJackson2MessageConverter
import org.springframework.messaging.simp.stomp.StompFrameHandler
import org.springframework.messaging.simp.stomp.StompHeaders
import org.springframework.messaging.simp.stomp.StompSession
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.web.socket.WebSocketHttpHeaders
import org.springframework.web.socket.client.standard.StandardWebSocketClient
import org.springframework.web.socket.messaging.WebSocketStompClient
import org.testcontainers.containers.MongoDBContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.lang.reflect.Type
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

@SpringBootTest(webEnvironment = RANDOM_PORT)
@Testcontainers
class SessionWebSocketIT {

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

    @field:LocalServerPort var port: Int = 0
    @Autowired lateinit var restTemplate: TestRestTemplate
    @Autowired lateinit var tokenIssuer: JwtTokenIssuer

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun tokenFor(userId: UserId = UserId.new()) =
        tokenIssuer.issue(userId, setOf(Role.PLAYER))

    private fun headersFor(token: String) = HttpHeaders().apply {
        setBearerAuth(token)
        set("Content-Type", "application/json")
    }

    private fun <T> post(path: String, body: Any, token: String, type: Class<T>) =
        restTemplate.exchange(path, HttpMethod.POST, HttpEntity(body, headersFor(token)), type)

    /** Connects a STOMP client and returns the session. Subscribes to /user/queue/session inside
     * [afterConnected] so that [SessionWelcome] is never missed. */
    private fun stompConnect(
        token: String,
        shareCode: String,
        onWelcome: ((Map<*, *>) -> Unit)? = null,
    ): StompSession {
        val client = WebSocketStompClient(StandardWebSocketClient()).apply {
            messageConverter = MappingJackson2MessageConverter()
        }
        val connectHeaders = StompHeaders().apply {
            set("Authorization", "Bearer $token")
            set("shareCode", shareCode)
        }
        return client.connectAsync(
            "ws://localhost:$port/ws",
            null as WebSocketHttpHeaders?,
            connectHeaders,
            object : StompSessionHandlerAdapter() {
                override fun afterConnected(session: StompSession, connectedHeaders: StompHeaders) {
                    if (onWelcome != null) {
                        session.subscribe("/app/session/$shareCode/welcome", mapFrameHandler(onWelcome))
                    }
                }
            },
        ).get(5, TimeUnit.SECONDS)
    }

    private fun mapFrameHandler(onFrame: (Map<*, *>) -> Unit) = object : StompFrameHandler {
        override fun getPayloadType(headers: StompHeaders): Type = Map::class.java

        @Suppress("UNCHECKED_CAST")
        override fun handleFrame(headers: StompHeaders, payload: Any?) {
            (payload as? Map<*, *>)?.let(onFrame)
        }
    }

    // -------------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------------

    @Test
    fun `SessionWelcome is received right after STOMP connect`() {
        val token = tokenFor()
        val shareCode = post(
            "/api/sessions",
            CreateSessionDto(gridId = "GRID01"),
            token,
            SessionCreationSuccess::class.java,
        ).body!!.shareCode

        val welcomeQueue = LinkedBlockingQueue<Map<*, *>>()
        stompConnect(token, shareCode, onWelcome = { welcomeQueue.offer(it) })

        val welcome = welcomeQueue.poll(3, TimeUnit.SECONDS)
        assertThat(welcome).isNotNull()
        assertThat(welcome!!["type"]).isEqualTo("SessionWelcome")
        assertThat(welcome["shareCode"]).isEqualTo(shareCode)
        assertThat(welcome["topicToSubscribe"]).isEqualTo("/topic/session/$shareCode")
    }

    @Test
    fun `grid update via STOMP broadcasts GridUpdated to sender`() {
        val token = tokenFor()
        val shareCode = post(
            "/api/sessions",
            CreateSessionDto(gridId = "GRID01"),
            token,
            SessionCreationSuccess::class.java,
        ).body!!.shareCode
        post("/api/sessions/start", StartSessionDto(shareCode = shareCode), token, Any::class.java)

        val received = LinkedBlockingQueue<Map<*, *>>()
        val session = stompConnect(token, shareCode)
        session.subscribe("/topic/session/$shareCode", mapFrameHandler { received.offer(it) })

        Thread.sleep(100) // let subscription propagate

        session.send(
            "/app/session/$shareCode/grid",
            mapOf("posX" to 2, "posY" to 3, "commandType" to "PLACE_LETTER", "letter" to "A"),
        )

        val event = received.poll(3, TimeUnit.SECONDS)
        assertThat(event).isNotNull()
        assertThat(event!!["type"]).isEqualTo("GridUpdated")
        assertThat(event["posX"]).isEqualTo(2)
        assertThat(event["posY"]).isEqualTo(3)
        assertThat(event["commandType"]).isEqualTo("PLACE_LETTER")
        assertThat(event["letter"]).isEqualTo("A")

        session.disconnect()
    }

    @Test
    fun `grid update is broadcast to all subscribers`() {
        val creatorToken = tokenFor()
        val joinerToken = tokenFor()

        val shareCode = post(
            "/api/sessions",
            CreateSessionDto(gridId = "GRID01"),
            creatorToken,
            SessionCreationSuccess::class.java,
        ).body!!.shareCode
        post("/api/sessions/join", JoinSessionDto(shareCode = shareCode), joinerToken, Any::class.java)
        post("/api/sessions/start", StartSessionDto(shareCode = shareCode), creatorToken, Any::class.java)

        val received1 = LinkedBlockingQueue<Map<*, *>>()
        val received2 = LinkedBlockingQueue<Map<*, *>>()

        val session1 = stompConnect(creatorToken, shareCode)
        val session2 = stompConnect(joinerToken, shareCode)
        session1.subscribe("/topic/session/$shareCode", mapFrameHandler { received1.offer(it) })
        session2.subscribe("/topic/session/$shareCode", mapFrameHandler { received2.offer(it) })

        Thread.sleep(100)

        session1.send(
            "/app/session/$shareCode/grid",
            mapOf("posX" to 0, "posY" to 0, "commandType" to "PLACE_LETTER", "letter" to "A"),
        )

        val event1 = received1.poll(3, TimeUnit.SECONDS)
        val event2 = received2.poll(3, TimeUnit.SECONDS)
        assertThat(event1).isNotNull()
        assertThat(event2).isNotNull()
        assertThat(event1!!["type"]).isEqualTo("GridUpdated")
        assertThat(event2!!["type"]).isEqualTo("GridUpdated")

        session1.disconnect()
        session2.disconnect()
    }
}
