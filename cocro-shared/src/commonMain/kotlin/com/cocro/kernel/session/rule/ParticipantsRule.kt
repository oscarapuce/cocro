package com.cocro.kernel.session.rule

import com.cocro.kernel.common.rule.CocroRule
import com.cocro.kernel.session.Participant
import com.cocro.kernel.session.enum.InviteStatus

object ParticipantsRule : CocroRule<Participant>() {
    override val arity: Int = -1
    const val MAX_ACTIVE_PARTICIPANTS = 4

    override fun isValid(values: List<Participant>): Boolean =
        values.isNotEmpty() &&
            countActiveParticipants(values) <= MAX_ACTIVE_PARTICIPANTS

    fun canJoin(participants: List<Participant>): Boolean = countActiveParticipants(participants) < MAX_ACTIVE_PARTICIPANTS

    fun countActiveParticipants(participants: List<Participant>): Int = participants.count { it.status == InviteStatus.JOINED }
}
