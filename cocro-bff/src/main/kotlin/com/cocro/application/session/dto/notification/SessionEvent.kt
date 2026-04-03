package com.cocro.application.session.dto.notification

import com.cocro.application.session.dto.ParticipantDto
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(SessionEvent.SessionWelcome::class, name = "SessionWelcome"),
    JsonSubTypes.Type(SessionEvent.ParticipantJoined::class, name = "ParticipantJoined"),
    JsonSubTypes.Type(SessionEvent.ParticipantLeft::class, name = "ParticipantLeft"),
    JsonSubTypes.Type(SessionEvent.GridUpdated::class, name = "GridUpdated"),
    JsonSubTypes.Type(SessionEvent.SyncRequired::class, name = "SyncRequired"),
    JsonSubTypes.Type(SessionEvent.GridChecked::class, name = "GridChecked"),
    JsonSubTypes.Type(SessionEvent.SessionEnded::class, name = "SessionEnded"),
    JsonSubTypes.Type(SessionEvent.SessionInterrupted::class, name = "SessionInterrupted"),
)
sealed interface SessionEvent {

    /**
     * Sent privately to a user right after their WS CONNECT.
     * Tells the client which topic to SUBSCRIBE to and gives the current session state.
     */
    @JsonTypeName("SessionWelcome")
    data class SessionWelcome(
        val shareCode: String,
        val topicToSubscribe: String,
        val participantCount: Int,
        val participants: List<ParticipantDto>,
        val status: String,
        val gridRevision: Long,
    ) : SessionEvent

    @JsonTypeName("ParticipantJoined")
    data class ParticipantJoined(
        val userId: String,
        val username: String,
        val participantCount: Int,
    ) : SessionEvent

    /**
     * @param reason "explicit" when the user called POST /leave,
     *               "timeout"  when the grace period expired after a STOMP disconnect.
     */
    @JsonTypeName("ParticipantLeft")
    data class ParticipantLeft(
        val userId: String,
        val username: String,
        val participantCount: Int,
        val reason: String = "explicit",
    ) : SessionEvent

    @JsonTypeName("GridUpdated")
    data class GridUpdated(
        val actorId: String,
        val posX: Int,
        val posY: Int,
        val commandType: String,
        val letter: Char?,
        val revision: Long,
    ) : SessionEvent

    /**
     * Sent privately to the user when their local revision is behind the cache.
     * The client should call POST /api/sessions/{code}/sync to resync.
     */
    @JsonTypeName("SyncRequired")
    data class SyncRequired(
        val currentRevision: Long,
    ) : SessionEvent

    @JsonTypeName("GridChecked")
    data class GridChecked(
        val userId: String,
        val isComplete: Boolean,
        val isCorrect: Boolean,
        val correctCount: Int,
        val totalCount: Int,
    ) : SessionEvent

    @JsonTypeName("SessionEnded")
    data class SessionEnded(
        val shareCode: String,
        val correctCount: Int,
        val totalCount: Int,
    ) : SessionEvent

    @JsonTypeName("SessionInterrupted")
    data class SessionInterrupted(
        val shareCode: String,
    ) : SessionEvent
}
