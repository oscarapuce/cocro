package com.cocro.application.session.mapper

import com.cocro.application.session.dto.CellStateDto
import com.cocro.application.session.dto.SessionCreationSuccess
import com.cocro.application.session.dto.SessionFullDto
import com.cocro.application.session.dto.SessionGridUpdateSuccess
import com.cocro.application.session.dto.SessionJoinSuccess
import com.cocro.application.session.dto.SessionLeaveSuccess
import com.cocro.application.session.dto.StartSessionSuccess
import com.cocro.application.session.dto.UpdateSessionGridDto
import com.cocro.kernel.auth.model.valueobject.UserId
import com.cocro.kernel.grid.model.CellPos
import com.cocro.kernel.session.enum.CommandType
import com.cocro.kernel.session.model.Session
import com.cocro.kernel.session.model.state.SessionGridCellState
import com.cocro.kernel.session.model.state.SessionGridCommand
import com.cocro.kernel.session.model.state.SessionGridState
import com.cocro.kernel.session.model.valueobject.SessionId
import com.cocro.kernel.session.rule.ParticipantsRule

internal fun Session.toSessionCreationSuccess(): SessionCreationSuccess =
    SessionCreationSuccess(
        sessionId = this.id.toString(),
        shareCode = this.shareCode.value,
    )

internal fun Session.toSessionJoinSuccess(): SessionJoinSuccess =
    SessionJoinSuccess(
        sessionId = this.id.toString(),
        participantCount = ParticipantsRule.countActiveParticipants(participants),
    )

internal fun Session.toSessionLeaveSuccess(): SessionLeaveSuccess =
    SessionLeaveSuccess(
        sessionId = this.id.toString(),
    )

internal fun Session.toStartSessionSuccess(): StartSessionSuccess =
    StartSessionSuccess(
        sessionId = this.id.toString(),
        participantCount = ParticipantsRule.countActiveParticipants(participants),
    )

internal fun UpdateSessionGridDto.toCommand(sessionId: SessionId, actorId: UserId): SessionGridCommand =
    when (CommandType.valueOf(this.commandType)) {
        CommandType.PLACE_LETTER -> this.toSetLetterCommand(sessionId, actorId)
        CommandType.ERASE_LETTER -> this.toClearCellCommand(sessionId, actorId)
    }

private fun UpdateSessionGridDto.toSetLetterCommand(sessionId: SessionId, actorId: UserId): SessionGridCommand.SetLetter =
    SessionGridCommand.SetLetter(
        sessionId = sessionId,
        actorId = actorId,
        position = CellPos(this.posX, this.posY),
        letter = this.letter!!,
    )

private fun UpdateSessionGridDto.toClearCellCommand(sessionId: SessionId, actorId: UserId): SessionGridCommand.ClearCell =
    SessionGridCommand.ClearCell(
        sessionId = sessionId,
        actorId = actorId,
        position = CellPos(this.posX, this.posY),
    )

internal fun UpdateSessionGridDto.toSuccess(sessionId: SessionId): SessionGridUpdateSuccess =
    SessionGridUpdateSuccess(
        sessionId = sessionId.toString(),
        commandType = this.commandType,
    )

internal fun Session.toSessionFullDto(
    gridState: SessionGridState,
    activeParticipantCount: Int,
): SessionFullDto {
    val template = this.gridTemplate
        ?: error("Session ${this.id} has no gridTemplate")
    return SessionFullDto(
        sessionId = this.id.toString(),
        shareCode = this.shareCode.value,
        status = this.status.name,
        participantCount = activeParticipantCount,
        topicToSubscribe = "/topic/session/${this.shareCode.value}",
        gridTemplate = template.toDto(),
        gridRevision = gridState.revision.value,
        cells = gridState.cells.mapNotNull { (pos, state) ->
            (state as? SessionGridCellState.Letter)?.let {
                CellStateDto(x = pos.x, y = pos.y, letter = it.value)
            }
        },
    )
}
