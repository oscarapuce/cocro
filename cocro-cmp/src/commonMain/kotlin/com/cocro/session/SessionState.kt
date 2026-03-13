package com.cocro.session

import com.cocro.network.dto.CellStateDto

/** Single source of truth for the session UI. */
sealed interface SessionUiState {

    /** Not connected to any session. */
    data object Idle : SessionUiState

    /** REST call or WebSocket handshake in progress. */
    data class Loading(val message: String) : SessionUiState

    /** WebSocket connected, session active. */
    data class Active(
        val sessionId: String,
        val shareCode: String,
        val status: SessionStatus,
        val participants: List<Participant>,
        val gridRevision: Long,
        val cells: List<CellStateDto>,
        val isCreator: Boolean,
    ) : SessionUiState

    data class Error(val message: String) : SessionUiState
}

enum class SessionStatus { CREATING, PLAYING, SCORING, ENDED, INTERRUPTED }

data class Participant(
    val userId: String,
    val username: String = "",
    val isOnline: Boolean = true,
    val cursorX: Int? = null,
    val cursorY: Int? = null,
)

fun SessionUiState.Active.participantCount(): Int = participants.size

fun SessionUiState.Active.canStart(): Boolean =
    isCreator && status == SessionStatus.CREATING && participants.isNotEmpty()
