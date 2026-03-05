package com.cocro.application.session.dto.notification

import com.fasterxml.jackson.annotation.JsonTypeInfo

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
sealed interface SessionEvent {

    /**
     * Sent privately to a user right after their WS CONNECT.
     * Tells the client which topic to SUBSCRIBE to and gives the current session state.
     */
    data class SessionWelcome(
        val shareCode: String,
        val topicToSubscribe: String,
        val participantCount: Int,
        val status: String,
        val gridRevision: Long,
    ) : SessionEvent

    data class ParticipantJoined(
        val userId: String,
        val participantCount: Int,
    ) : SessionEvent

    /**
     * @param reason "explicit" when the user called POST /leave,
     *               "timeout"  when the grace period expired after a STOMP disconnect.
     */
    data class ParticipantLeft(
        val userId: String,
        val participantCount: Int,
        val reason: String = "explicit",
    ) : SessionEvent

    data class SessionStarted(
        val participantCount: Int,
    ) : SessionEvent

    data class GridUpdated(
        val actorId: String,
        val posX: Int,
        val posY: Int,
        val commandType: String,
        val letter: Char?,
    ) : SessionEvent

    /**
     * Sent privately to the user when their local revision is behind the cache.
     * The client should call GET /api/sessions/{code}/state to resync.
     */
    data class SyncRequired(
        val currentRevision: Long,
    ) : SessionEvent
}
