package com.cocro.infrastructure.persistence.mongo.session.mapper

import com.cocro.infrastructure.persistence.mongo.grid.document.CellDocument
import com.cocro.infrastructure.persistence.mongo.session.document.ParticipantDocument
import com.cocro.infrastructure.persistence.mongo.session.document.SessionDocument
import com.cocro.infrastructure.persistence.mongo.session.document.SessionGridStateDocument
import com.cocro.kernel.auth.model.valueobject.UserId
import com.cocro.kernel.grid.model.CellPos
import com.cocro.kernel.grid.model.valueobject.GridShareCode
import com.cocro.kernel.session.Participant
import com.cocro.kernel.session.enum.InviteStatus
import com.cocro.kernel.session.enum.SessionStatus
import com.cocro.kernel.session.model.Session
import com.cocro.kernel.session.model.state.SessionGridCellState
import com.cocro.kernel.session.model.state.SessionGridState
import com.cocro.kernel.session.model.state.SessionGridStateRevision
import com.cocro.kernel.session.model.valueobject.SessionId
import com.cocro.kernel.session.model.valueobject.SessionShareCode

fun Session.toDocument(): SessionDocument =
    SessionDocument(
        id = id.value,
        shareCode = shareCode.toString(),
        creatorId = creatorId.toString(),
        gridShortId = gridId.toString(),
        status = status.name,
        participants = participants.map { it.toDocument() }.toSet(),
        sessionGridState = sessionGridState.toDocument(),
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

fun Participant.toDocument(): ParticipantDocument =
    ParticipantDocument(
        userId = userId.value,
        status = status.name,
    )

fun SessionGridState.toDocument(): SessionGridStateDocument =
    SessionGridStateDocument(
        sessionShareCode = sessionId.toString(),
        gridShortId = gridShareCode.toString(),
        revision = revision.value,
        cells =
            cells.entries.map { (pos, state) ->
                when (state) {
                    is SessionGridCellState.Letter ->
                        CellDocument(
                            x = pos.x,
                            y = pos.y,
                            type = "LETTER",
                            letter = state.value,
                        )
                }
            },
    )

fun SessionDocument.toDomain(): Session =
    Session.rehydrate(
        id = SessionId(id),
        shareCode = SessionShareCode(shareCode),
        creatorId = UserId.from(creatorId),
        gridId = GridShareCode(gridShortId),
        status = SessionStatus.valueOf(status),
        participants = participants.map { it.toDomain() },
        sessionGridState = sessionGridState.toDomain(),
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

fun ParticipantDocument.toDomain(): Participant =
    Participant(
        userId = UserId(userId),
        status = InviteStatus.valueOf(status),
    )

fun SessionGridStateDocument.toDomain(): SessionGridState =
    SessionGridState(
        sessionId = SessionShareCode(sessionShareCode),
        gridShareCode = GridShareCode(gridShortId),
        revision = SessionGridStateRevision(revision),
        cells =
            cells
                .filter { it.type == "LETTER" && it.letter != null }
                .associate { cell ->
                    CellPos(cell.x, cell.y) to SessionGridCellState.Letter(cell.letter!!)
                },
    )
