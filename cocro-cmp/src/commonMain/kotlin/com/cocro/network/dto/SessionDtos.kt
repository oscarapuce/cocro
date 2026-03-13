package com.cocro.network.dto

import kotlinx.serialization.Serializable

// ---- REST request DTOs ----

@Serializable data class CreateSessionRequest(val gridId: String)
@Serializable data class JoinSessionRequest(val shareCode: String)
@Serializable data class LeaveSessionRequest(val shareCode: String)
@Serializable data class StartSessionRequest(val shareCode: String)

// ---- REST response DTOs ----

@Serializable data class CreateSessionResponse(val sessionId: String, val shareCode: String)
@Serializable data class JoinSessionResponse(val sessionId: String, val participantCount: Int)
@Serializable data class LeaveSessionResponse(val sessionId: String)
@Serializable data class StartSessionResponse(val sessionId: String, val participantCount: Int)

@Serializable
data class SessionStateResponse(
    val sessionId: String,
    val shareCode: String,
    val revision: Long,
    val cells: List<CellStateDto>,
)

@Serializable
data class CellStateDto(val x: Int, val y: Int, val letter: Char)

// ---- WebSocket outbound payload ----

@Serializable
data class GridUpdatePayload(
    val posX: Int,
    val posY: Int,
    val commandType: String,   // "PLACE_LETTER" | "ERASE_LETTER"
    val letter: Char? = null,
)

// ---- WebSocket inbound events (mirrors server SessionEvent with @JsonTypeInfo type discriminator) ----

/**
 * Raw event frame received from the server.
 * The server serializes sealed SessionEvent subclasses with Jackson @JsonTypeInfo(property = "type"),
 * using the simple class name as discriminator value.
 */
@Serializable
data class RawSessionEvent(
    val type: String,
    // SessionWelcome
    val shareCode: String? = null,
    val topicToSubscribe: String? = null,
    val status: String? = null,
    val gridRevision: Long? = null,
    // ParticipantJoined / ParticipantLeft
    val userId: String? = null,
    val participantCount: Int? = null,
    val reason: String? = null,
    // SessionStarted — participantCount already above
    // GridUpdated
    val actorId: String? = null,
    val posX: Int? = null,
    val posY: Int? = null,
    val commandType: String? = null,
    val letter: Char? = null,
    // SyncRequired
    val currentRevision: Long? = null,
)

/** Strongly-typed client-side event model. */
sealed interface ClientSessionEvent {

    data class Welcome(
        val shareCode: String,
        val topicToSubscribe: String,
        val participantCount: Int,
        val status: String,
        val gridRevision: Long,
    ) : ClientSessionEvent

    data class ParticipantJoined(
        val userId: String,
        val participantCount: Int,
    ) : ClientSessionEvent

    data class ParticipantLeft(
        val userId: String,
        val participantCount: Int,
        val reason: String,
    ) : ClientSessionEvent

    data class SessionStarted(val participantCount: Int) : ClientSessionEvent

    data class GridUpdated(
        val actorId: String,
        val posX: Int,
        val posY: Int,
        val commandType: String,
        val letter: Char?,
    ) : ClientSessionEvent

    data class SyncRequired(val currentRevision: Long) : ClientSessionEvent

    data class Unknown(val type: String) : ClientSessionEvent
}

fun RawSessionEvent.toClientEvent(): ClientSessionEvent = when (type) {
    "SessionWelcome" -> ClientSessionEvent.Welcome(
        shareCode = shareCode ?: "",
        topicToSubscribe = topicToSubscribe ?: "",
        participantCount = participantCount ?: 0,
        status = status ?: "",
        gridRevision = gridRevision ?: 0L,
    )
    "ParticipantJoined" -> ClientSessionEvent.ParticipantJoined(
        userId = userId ?: "",
        participantCount = participantCount ?: 0,
    )
    "ParticipantLeft" -> ClientSessionEvent.ParticipantLeft(
        userId = userId ?: "",
        participantCount = participantCount ?: 0,
        reason = reason ?: "explicit",
    )
    "SessionStarted" -> ClientSessionEvent.SessionStarted(participantCount ?: 0)
    "GridUpdated" -> ClientSessionEvent.GridUpdated(
        actorId = actorId ?: "",
        posX = posX ?: 0,
        posY = posY ?: 0,
        commandType = commandType ?: "",
        letter = letter,
    )
    "SyncRequired" -> ClientSessionEvent.SyncRequired(currentRevision ?: 0L)
    else -> ClientSessionEvent.Unknown(type)
}
