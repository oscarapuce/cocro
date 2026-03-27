package com.cocro.domain.session.rule

import com.cocro.domain.auth.model.valueobject.UserId
import com.cocro.domain.session.Participant
import com.cocro.domain.session.enum.ParticipantStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ParticipantsRuleTest {

    @Test
    fun `isValid should return true when there is at least one participant`() {
        // given
        val participants = listOf(Participant(UserId.new(), ParticipantStatus.JOINED))

        // when
        val result = ParticipantsRule.validate(*participants.toTypedArray())

        // then
        assertThat(result).isTrue()
    }

    @Test
    fun `isValid should return true when list is empty`() {
        // given
        val participants = emptyList<Participant>()

        // when
        val result = ParticipantsRule.validate(*participants.toTypedArray())

        // then
        assertThat(result).isTrue()
    }

    @Test
    fun `isValid should return true with exactly max active participants`() {
        // given
        val participants = (1..ParticipantsRule.MAX_ACTIVE_PARTICIPANTS)
            .map { Participant(UserId.new(), ParticipantStatus.JOINED) }

        // when
        val result = ParticipantsRule.validate(*participants.toTypedArray())

        // then
        assertThat(result).isTrue()
    }

    @Test
    fun `isValid should return false when active participants exceed max`() {
        // given
        val participants = (1..ParticipantsRule.MAX_ACTIVE_PARTICIPANTS + 1)
            .map { Participant(UserId.new(), ParticipantStatus.JOINED) }

        // when
        val result = ParticipantsRule.validate(*participants.toTypedArray())

        // then
        assertThat(result).isFalse()
    }

    @Test
    fun `canJoin should return true when active participants are below max`() {
        // given
        val participants = listOf(Participant(UserId.new(), ParticipantStatus.JOINED))

        // when
        val result = ParticipantsRule.canJoin(participants)

        // then
        assertThat(result).isTrue()
    }

    @Test
    fun `canJoin should return false when active participants are at max`() {
        // given
        val participants = (1..ParticipantsRule.MAX_ACTIVE_PARTICIPANTS)
            .map { Participant(UserId.new(), ParticipantStatus.JOINED) }

        // when
        val result = ParticipantsRule.canJoin(participants)

        // then
        assertThat(result).isFalse()
    }

    @Test
    fun `countActiveParticipants should ignore non-joined participants`() {
        // given
        val participants = listOf(
            Participant(UserId.new(), ParticipantStatus.JOINED),
            Participant(UserId.new(), ParticipantStatus.LEFT),
            Participant(UserId.new(), ParticipantStatus.LEFT),
        )

        // when
        val count = ParticipantsRule.countActiveParticipants(participants)

        // then
        assertThat(count).isEqualTo(1)
    }
}
