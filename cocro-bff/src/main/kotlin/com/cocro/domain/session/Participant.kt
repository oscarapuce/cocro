package com.cocro.domain.session

import com.cocro.domain.auth.model.valueobject.UserId
import com.cocro.domain.session.enum.ParticipantStatus

data class Participant(
    val userId: UserId,
    val username: String,
    val status: ParticipantStatus,
) {
    companion object {
        fun joined(userId: UserId, username: String): Participant = Participant(userId, username, ParticipantStatus.JOINED)
    }
}
