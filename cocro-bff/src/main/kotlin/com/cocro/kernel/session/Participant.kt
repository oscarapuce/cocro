package com.cocro.kernel.session

import com.cocro.kernel.auth.model.valueobject.UserId
import com.cocro.kernel.session.enum.InviteStatus

data class Participant(
    val userId: UserId,
    val status: InviteStatus,
) {
    companion object {
        fun joined(userId: UserId): Participant = Participant(userId, InviteStatus.JOINED)
    }
}
