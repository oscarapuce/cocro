package com.cocro.infrastructure.persistence.mongo.session.mapper

import com.cocro.application.session.mapper.toDocument
import com.cocro.application.session.mapper.toSnapshot
import com.cocro.infrastructure.persistence.mongo.grid.document.CellDocument
import com.cocro.infrastructure.persistence.mongo.session.document.ParticipantDocument
import com.cocro.infrastructure.persistence.mongo.session.document.SessionDocument
import com.cocro.infrastructure.persistence.mongo.session.document.SessionGridStateDocument
import com.cocro.domain.auth.model.valueobject.UserId
import com.cocro.domain.grid.model.CellPos
import com.cocro.domain.grid.model.valueobject.GridShareCode
import com.cocro.domain.session.Participant
import com.cocro.domain.session.enum.InviteStatus
import com.cocro.domain.session.enum.SessionStatus
import com.cocro.domain.session.model.Session
import com.cocro.domain.session.model.state.SessionGridCellState
import com.cocro.domain.session.model.state.SessionGridState
import com.cocro.domain.session.model.state.SessionGridStateRevision
import com.cocro.domain.session.model.valueobject.SessionId
import com.cocro.domain.session.model.valueobject.SessionShareCode

fun Session.toDocument(): SessionDocument =
    SessionDocument(
        id = id.toString(),
        shareCode = shareCode.toString(),
        creatorId = creatorId.toString(),
        gridShortId = gridId.toString(),
        gridTemplate = gridTemplate?.toDocument()
            ?: error("Session ${id} has no gridTemplate — cannot persist"),
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
        sessionId = sessionId.value,
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
        id = SessionId.from(id),
        shareCode = SessionShareCode(shareCode),
        creatorId = UserId.from(creatorId),
        gridId = GridShareCode(gridShortId),
        status = when (status) {
            "CREATING", "SCORING" -> SessionStatus.PLAYING  // migration fallback
            else -> SessionStatus.valueOf(status)
        },
        participants = participants.map { it.toDomain() },
        sessionGridState = sessionGridState.toDomain(),
        createdAt = createdAt,
        updatedAt = updatedAt,
        gridTemplate = gridTemplate?.toSnapshot(),
    )

fun ParticipantDocument.toDomain(): Participant =
    Participant(
        userId = UserId(userId),
        status = InviteStatus.valueOf(status),
    )

fun SessionGridStateDocument.toDomain(): SessionGridState =
    SessionGridState(
        sessionId = SessionId(sessionId),
        gridShareCode = GridShareCode(gridShortId),
        revision = SessionGridStateRevision(revision),
        cells =
            cells
                .filter { it.type == "LETTER" && it.letter != null }
                .associate { cell ->
                    CellPos(cell.x, cell.y) to SessionGridCellState.Letter(cell.letter!!)
                },
    )
