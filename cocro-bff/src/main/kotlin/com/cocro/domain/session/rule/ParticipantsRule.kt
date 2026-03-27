package com.cocro.domain.session.rule

import com.cocro.domain.common.rule.CocroRule
import com.cocro.domain.session.Participant
import com.cocro.domain.session.enum.ParticipantStatus

object ParticipantsRule : CocroRule<Participant>() {
    override val arity: Int = -1
    const val MAX_ACTIVE_PARTICIPANTS = 4

    override fun isValid(values: List<Participant>): Boolean =
        countActiveParticipants(values) <= MAX_ACTIVE_PARTICIPANTS

    fun canJoin(participants: List<Participant>): Boolean = countActiveParticipants(participants) < MAX_ACTIVE_PARTICIPANTS

    fun countActiveParticipants(participants: List<Participant>): Int = participants.count { it.status == ParticipantStatus.JOINED }
}
