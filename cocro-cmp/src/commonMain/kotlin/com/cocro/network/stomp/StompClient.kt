package com.cocro.network.stomp

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

/**
 * Minimal STOMP 1.2 client over Ktor WebSocket.
 *
 * Lifecycle:
 *   1. Call [frames] to open a connection and start the STOMP handshake.
 *   2. Collect the returned [Flow<StompFrame>] — each emission is a MESSAGE frame.
 *   3. Use [subscribe] / [send] / [disconnect] on the *same* instance while the flow is active.
 *
 * The flow completes when the WebSocket closes (server or client side).
 */
class StompClient(private val httpClient: HttpClient) {

    private var subscriptionCounter = 0
    private var _outgoing: SendChannel<Frame>? = null

    // -------------------------------------------------------------------------
    // Connection
    // -------------------------------------------------------------------------

    /**
     * Opens a WebSocket connection, performs the STOMP CONNECT handshake, and
     * returns a [Flow] of incoming STOMP frames (MESSAGE, ERROR, RECEIPT, …).
     *
     * @param wsUrl         WebSocket URL, e.g. "ws://10.0.2.2:8080/ws"
     * @param connectHeaders  Extra STOMP CONNECT headers (Authorization, shareCode, …)
     */
    fun frames(
        wsUrl: String,
        connectHeaders: Map<String, String>,
        scope: CoroutineScope,
    ): Flow<StompFrame> = callbackFlow {
        httpClient.webSocket(wsUrl) {
            _outgoing = outgoing

            // --- STOMP CONNECT ---
            val connectFrame = StompFrame(
                command = StompCommand.CONNECT,
                headers = buildMap {
                    put("accept-version", "1.2")
                    put("heart-beat", "10000,10000")
                    putAll(connectHeaders)
                },
            )
            outgoing.send(Frame.Text(connectFrame.serialize()))

            // --- Await CONNECTED ---
            val connectedRaw = (incoming.receive() as? Frame.Text)?.readText() ?: ""
            val connectedFrame = StompFrame.parse(connectedRaw)
            if (connectedFrame.command != StompCommand.CONNECTED) {
                close(IllegalStateException("Expected CONNECTED, got ${connectedFrame.command}"))
                return@webSocket
            }

            // --- Forward incoming MESSAGE frames ---
            try {
                for (wsFrame in incoming) {
                    if (wsFrame is Frame.Text) {
                        val stompFrame = StompFrame.parse(wsFrame.readText())
                        trySend(stompFrame)
                    }
                }
            } catch (_: ClosedReceiveChannelException) {
                // normal close
            } finally {
                _outgoing = null
                close() // close the callbackFlow
            }
        }

        awaitClose {
            _outgoing = null
        }
    }

    // -------------------------------------------------------------------------
    // Client commands
    // -------------------------------------------------------------------------

    /** Subscribe to a STOMP destination. Returns the generated subscription id. */
    fun subscribe(destination: String): String {
        val id = "sub-${++subscriptionCounter}"
        sendFrame(
            StompFrame(
                command = StompCommand.SUBSCRIBE,
                headers = mapOf("id" to id, "destination" to destination, "ack" to "auto"),
            ),
        )
        return id
    }

    /** Send a message to a destination with an optional JSON body. */
    fun send(destination: String, body: String, contentType: String = "application/json") {
        sendFrame(
            StompFrame(
                command = StompCommand.SEND,
                headers = mapOf(
                    "destination" to destination,
                    "content-type" to contentType,
                    "content-length" to body.length.toString(),
                ),
                body = body,
            ),
        )
    }

    /** Gracefully close the STOMP session. */
    fun disconnect() {
        sendFrame(StompFrame(command = StompCommand.DISCONNECT))
        _outgoing = null
    }

    // -------------------------------------------------------------------------

    private fun sendFrame(frame: StompFrame) {
        _outgoing?.trySend(Frame.Text(frame.serialize()))
    }
}
