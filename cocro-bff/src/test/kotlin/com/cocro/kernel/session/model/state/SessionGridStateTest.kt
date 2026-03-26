package com.cocro.kernel.session.model.state

import com.cocro.kernel.auth.model.valueobject.UserId
import com.cocro.kernel.grid.model.CellPos
import com.cocro.kernel.grid.model.valueobject.GridShareCode
import com.cocro.kernel.session.model.valueobject.SessionId
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class SessionGridStateTest {

    private val sessionId = SessionId.new()
    private val gridShareCode = GridShareCode("ABC123")
    private val actorId = UserId.new()

    @Test
    fun `initial state should have empty cells and revision 0`() {
        // when
        val state = SessionGridState.initial(sessionId, gridShareCode)

        // then
        assertThat(state.cells).isEmpty()
        assertThat(state.revision.value).isEqualTo(0L)
        assertThat(state.sessionId).isEqualTo(sessionId)
        assertThat(state.gridShareCode).isEqualTo(gridShareCode)
    }

    @Test
    fun `apply SetLetter should add letter at given position`() {
        // given
        val state = SessionGridState.initial(sessionId, gridShareCode)
        val position = CellPos(0, 0)
        val command = SessionGridCommand.SetLetter(
            sessionId = sessionId,
            actorId = actorId,
            position = position,
            letter = 'A',
        )

        // when
        val newState = state.apply(command)

        // then
        assertThat(newState.cells[position]).isEqualTo(SessionGridCellState.Letter('A'))
    }

    @Test
    fun `apply SetLetter should increment revision`() {
        // given
        val state = SessionGridState.initial(sessionId, gridShareCode)
        val command = SessionGridCommand.SetLetter(
            sessionId = sessionId,
            actorId = actorId,
            position = CellPos(1, 2),
            letter = 'B',
        )

        // when
        val newState = state.apply(command)

        // then
        assertThat(newState.revision.value).isEqualTo(1L)
    }

    @Test
    fun `apply SetLetter should overwrite existing letter at same position`() {
        // given
        val position = CellPos(0, 0)
        val state = SessionGridState.initial(sessionId, gridShareCode)
            .apply(SessionGridCommand.SetLetter(sessionId, actorId, position, 'A'))

        val command = SessionGridCommand.SetLetter(
            sessionId = sessionId,
            actorId = actorId,
            position = position,
            letter = 'Z',
        )

        // when
        val newState = state.apply(command)

        // then
        assertThat(newState.cells[position]).isEqualTo(SessionGridCellState.Letter('Z'))
    }

    @Test
    fun `apply SetLetter with invalid lowercase letter should throw`() {
        // given
        val state = SessionGridState.initial(sessionId, gridShareCode)
        val command = SessionGridCommand.SetLetter(
            sessionId = sessionId,
            actorId = actorId,
            position = CellPos(0, 0),
            letter = 'a',
        )

        // when / then
        assertThatThrownBy { state.apply(command) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Invalid letter")
    }

    @Test
    fun `apply ClearCell should remove letter at given position`() {
        // given
        val position = CellPos(0, 0)
        val state = SessionGridState.initial(sessionId, gridShareCode)
            .apply(SessionGridCommand.SetLetter(sessionId, actorId, position, 'A'))

        val command = SessionGridCommand.ClearCell(
            sessionId = sessionId,
            actorId = actorId,
            position = position,
        )

        // when
        val newState = state.apply(command)

        // then
        assertThat(newState.cells).doesNotContainKey(position)
    }

    @Test
    fun `apply ClearCell should increment revision`() {
        // given
        val position = CellPos(0, 0)
        val state = SessionGridState.initial(sessionId, gridShareCode)
            .apply(SessionGridCommand.SetLetter(sessionId, actorId, position, 'A'))

        val command = SessionGridCommand.ClearCell(sessionId, actorId, position)

        // when
        val newState = state.apply(command)

        // then
        assertThat(newState.revision.value).isEqualTo(2L)
    }

    @Test
    fun `apply ClearCell on empty position should not fail`() {
        // given
        val state = SessionGridState.initial(sessionId, gridShareCode)
        val command = SessionGridCommand.ClearCell(
            sessionId = sessionId,
            actorId = actorId,
            position = CellPos(5, 5),
        )

        // when
        val newState = state.apply(command)

        // then
        assertThat(newState.cells).isEmpty()
        assertThat(newState.revision.value).isEqualTo(1L)
    }
}
