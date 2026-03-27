package com.cocro.domain.session

import com.cocro.domain.auth.model.valueobject.UserId
import com.cocro.domain.session.enum.InviteStatus

data class Participant(
    val userId: UserId,
    val status: InviteStatus,
) {
    companion object {
        fun joined(userId: UserId): Participant = Participant(userId, InviteStatus.JOINED)
    }
}
