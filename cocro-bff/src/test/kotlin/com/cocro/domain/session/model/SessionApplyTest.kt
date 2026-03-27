package com.cocro.domain.session.model

import com.cocro.domain.auth.model.valueobject.UserId
import com.cocro.domain.common.CocroResult
import com.cocro.domain.grid.model.GridTemplateSnapshot
import com.cocro.domain.grid.model.valueobject.GridShareCode
import com.cocro.domain.session.enum.InviteStatus
import com.cocro.domain.session.enum.SessionStatus
import com.cocro.domain.session.error.SessionError
import com.cocro.domain.session.model.valueobject.SessionShareCode
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class SessionApplyTest {

    private val creatorId = UserId.new()
    private val shareCode = SessionShareCode("AB12")
    private val session = Session.create(
        creatorId = creatorId,
        shareCode = shareCode,
        gridId = GridShareCode("GRID01"),
        gridTemplate = minimalSnapshot(),
    )

    private fun minimalSnapshot() = GridTemplateSnapshot(
        shortId = GridShareCode("GRID01"),
        title = "Test", width = 5, height = 5,
        difficulty = null, author = null, reference = null,
        description = null, globalClueLabel = null,
        globalClueWordLengths = null, cells = emptyList()
    )

    // -------------------------------------------------------------------------
    // JOIN
    // -------------------------------------------------------------------------

    @Nested
    inner class Join {

        @Test
        fun `should add participant when session is PLAYING`() {
            val joiner = UserId.new()
            val playing = session.withStatus(SessionStatus.PLAYING)

            val result = playing.apply(SessionLifecycleCommand.Join(joiner))

            assertThat(result).isInstanceOf(CocroResult.Success::class.java)
        }

        @Test
        fun `should fail when user is already a JOINED participant`() {
            val joiner = UserId.new()
            val sessionWithJoiner = session.join(joiner)

            val result = sessionWithJoiner.apply(SessionLifecycleCommand.Join(joiner))

            assertThat(result).isInstanceOf(CocroResult.Error::class.java)
            val errors = (result as CocroResult.Error).errors
            assertThat(errors).anyMatch { it is SessionError.AlreadyParticipant }
        }

        @Test
        fun `should fail when session is full`() {
            val fullSession = (1..4).fold(session) { s, _ -> s.join(UserId.new()) }
            val joiner = UserId.new()

            val result = fullSession.apply(SessionLifecycleCommand.Join(joiner))

            assertThat(result).isInstanceOf(CocroResult.Error::class.java)
            val errors = (result as CocroResult.Error).errors
            assertThat(errors).contains(SessionError.SessionFull)
        }

        @Test
        fun `should fail when session is ENDED`() {
            val ended = session.withStatus(SessionStatus.ENDED)
            val joiner = UserId.new()

            val result = ended.apply(SessionLifecycleCommand.Join(joiner))

            assertThat(result).isInstanceOf(CocroResult.Error::class.java)
            val errors = (result as CocroResult.Error).errors
            assertThat(errors).anyMatch { it is SessionError.InvalidStatusForAction }
        }

        @Test
        fun `should allow join on INTERRUPTED session and resume to PLAYING`() {
            val interrupted = session.withStatus(SessionStatus.INTERRUPTED)
            val joiner = UserId.new()

            val result = interrupted.apply(SessionLifecycleCommand.Join(joiner))

            assertThat(result).isInstanceOf(CocroResult.Success::class.java)
            val updated = (result as CocroResult.Success).value
            assertThat(updated.status).isEqualTo(SessionStatus.PLAYING)
            assertThat(updated.participants).anyMatch { it.userId == joiner && it.status == InviteStatus.JOINED }
        }

        @Test
        fun `should allow user with LEFT status to rejoin (flip in-place)`() {
            val joiner = UserId.new()
            val sessionAfterLeave = session.join(joiner).leave(joiner)

            val result = sessionAfterLeave.apply(SessionLifecycleCommand.Join(joiner))

            assertThat(result).isInstanceOf(CocroResult.Success::class.java)
            val updated = (result as CocroResult.Success).value
            // In-place flip: exactly one entry for joiner, status JOINED
            assertThat(updated.participants.filter { it.userId == joiner }).hasSize(1)
            assertThat(updated.participants).anyMatch { it.userId == joiner && it.status == InviteStatus.JOINED }
        }

        @Test
        fun `LEFT participant does not count toward capacity`() {
            // Fill with 4, then one leaves — a new user can join
            val ids = (1..4).map { UserId.new() }
            val filled = ids.fold(session) { s, id -> s.join(id) }
            val afterLeave = filled.leave(ids[0])

            val joiner = UserId.new()
            val result = afterLeave.apply(SessionLifecycleCommand.Join(joiner))

            assertThat(result).isInstanceOf(CocroResult.Success::class.java)
        }

    }

    // -------------------------------------------------------------------------
    // LEAVE
    // -------------------------------------------------------------------------

    @Nested
    inner class Leave {

        @Test
        fun `should mark participant as LEFT`() {
            val participant = UserId.new()
            val sessionWithParticipant = session.join(participant)

            val result = sessionWithParticipant.apply(SessionLifecycleCommand.Leave(participant))

            assertThat(result).isInstanceOf(CocroResult.Success::class.java)
            val updated = (result as CocroResult.Success).value
            assertThat(updated.participants)
                .anyMatch { it.userId == participant && it.status == InviteStatus.LEFT }
        }

        @Test
        fun `should fail when user is not a participant`() {
            val outsider = UserId.new()

            val result = session.apply(SessionLifecycleCommand.Leave(outsider))

            assertThat(result).isInstanceOf(CocroResult.Error::class.java)
            val errors = (result as CocroResult.Error).errors
            assertThat(errors).anyMatch { it is SessionError.UserNotParticipant }
        }

        @Test
        fun `should fail when user already left`() {
            val participant = UserId.new()
            val sessionAfterLeave = session.join(participant).leave(participant)

            val result = sessionAfterLeave.apply(SessionLifecycleCommand.Leave(participant))

            assertThat(result).isInstanceOf(CocroResult.Error::class.java)
            assertThat((result as CocroResult.Error).errors).anyMatch { it is SessionError.UserNotParticipant }
        }
    }

    // -------------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------------

    private fun Session.withStatus(status: SessionStatus): Session =
        Session.rehydrate(id, shareCode, creatorId, gridId, status, participants, sessionGridState, createdAt, updatedAt, gridTemplate = gridTemplate)

}
