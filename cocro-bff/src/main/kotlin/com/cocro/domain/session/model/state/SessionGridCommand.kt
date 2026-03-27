package com.cocro.domain.session.model.state

import com.cocro.domain.auth.model.valueobject.UserId
import com.cocro.domain.grid.model.CellPos
import com.cocro.domain.session.model.valueobject.SessionId
import com.cocro.domain.session.model.valueobject.SessionShareCode

sealed interface SessionGridCommand {
    val sessionId: SessionId
    val actorId: UserId

    data class SetLetter(
        override val sessionId: SessionId,
        override val actorId: UserId,
        val position: CellPos,
        val letter: Char,
    ) : SessionGridCommand

    data class ClearCell(
        override val sessionId: SessionId,
        override val actorId: UserId,
        val position: CellPos,
    ) : SessionGridCommand
}
