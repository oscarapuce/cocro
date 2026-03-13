package com.cocro.session

import com.cocro.network.dto.ClientSessionEvent
import com.cocro.network.dto.GridUpdatePayload
import com.cocro.network.dto.RawSessionEvent
import com.cocro.network.dto.toClientEvent
import com.cocro.network.http.appJson
import com.cocro.network.stomp.StompClient
import com.cocro.network.stomp.StompCommand
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.serialization.encodeToString

/**
 * High-level session WebSocket client.
 *
 * Manages the two-step STOMP subscription dance:
 *  1. Subscribe to /user/queue/session → receive [ClientSessionEvent.Welcome]
 *  2. Extract topicToSubscribe → subscribe to /topic/session/{shareCode}
 *  3. Emit all events (Welcome included) to callers via [events]
 *
 * Usage:
 *  ```
 *  val client = SessionWebSocketClient(stompClient, wsBaseUrl, token, shareCode, scope)
 *  client.events.collect { event -> ... }
 *  client.sendGridUpdate(0, 0, "PLACE_LETTER", 'A')
 *  client.disconnect()
 *  ```
 */
class SessionWebSocketClient(
    private val stompClient: StompClient,
    private val wsBaseUrl: String,
    private val token: String,
    private val shareCode: String,
    scope: CoroutineScope,
) {
    // Shared, hot flow of all incoming STOMP MESSAGE frames
    private val stompFrames = stompClient.frames(
        wsUrl = "$wsBaseUrl/ws",
        connectHeaders = mapOf(
            "Authorization" to "Bearer $token",
            "shareCode" to shareCode,
        ),
        scope = scope,
    ).shareIn(scope, SharingStarted.Eagerly, replay = 0)

    /**
     * Flow of strongly-typed [ClientSessionEvent].
     *
     * Automatically subscribes to the private queue on start,
     * and to the broadcast topic once [ClientSessionEvent.Welcome] is received.
     */
    val events: Flow<ClientSessionEvent> = stompFrames
        .onEach { frame ->
            if (frame.command == com.cocro.network.stomp.StompCommand.CONNECTED) {
                // Subscribe to private queue for SessionWelcome
                stompClient.subscribe("/user/queue/session")
            }
        }
        .mapNotNull { frame ->
            if (frame.command != StompCommand.MESSAGE) return@mapNotNull null
            val body = frame.headers["body"] ?: frame.body ?: return@mapNotNull null
            runCatching { appJson.decodeFromString<RawSessionEvent>(body).toClientEvent() }.getOrNull()
        }
        .onEach { event ->
            // When we receive the Welcome, subscribe to the broadcast topic
            if (event is ClientSessionEvent.Welcome) {
                stompClient.subscribe(event.topicToSubscribe)
            }
        }
        .shareIn(scope, SharingStarted.Eagerly, replay = 0)

    // -------------------------------------------------------------------------
    // Outbound commands
    // -------------------------------------------------------------------------

    fun sendGridUpdate(posX: Int, posY: Int, commandType: String, letter: Char? = null) {
        val payload = appJson.encodeToString(GridUpdatePayload(posX, posY, commandType, letter))
        stompClient.send("/app/session/$shareCode/grid", payload)
    }

    fun disconnect() = stompClient.disconnect()
}
