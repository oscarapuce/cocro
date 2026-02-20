package com.cocro.kernel.session.model.state

import com.cocro.kernel.auth.model.valueobject.UserId
import com.cocro.kernel.grid.model.CellPos
import com.cocro.kernel.session.model.valueobject.SessionShareCode

sealed interface SessionGridCommand {
    val sessionId: SessionShareCode
    val actorId: UserId

    data class SetLetter(
        override val sessionId: SessionShareCode,
        override val actorId: UserId,
        val position: CellPos,
        val letter: Char,
    ) : SessionGridCommand

    data class ClearCell(
        override val sessionId: SessionShareCode,
        override val actorId: UserId,
        val position: CellPos,
    ) : SessionGridCommand
}
