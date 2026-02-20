package com.cocro.application.session.mapper

import com.cocro.application.session.dto.SessionCreationSuccess
import com.cocro.application.session.dto.SessionGridUpdateSuccess
import com.cocro.application.session.dto.SessionJoinSuccess
import com.cocro.application.session.dto.SessionLeaveSuccess
import com.cocro.application.session.dto.StartSessionSuccess
import com.cocro.application.session.dto.UpdateSessionGridDto
import com.cocro.kernel.auth.model.valueobject.UserId
import com.cocro.kernel.grid.model.CellPos
import com.cocro.kernel.session.enum.CommandType
import com.cocro.kernel.session.model.Session
import com.cocro.kernel.session.model.state.SessionGridCommand
import com.cocro.kernel.session.model.valueobject.SessionShareCode
import com.cocro.kernel.session.rule.ParticipantsRule

internal fun Session.toSessionCreationSuccess(): SessionCreationSuccess =
    SessionCreationSuccess(
        shareCode = this.shareCode.value,
    )

internal fun Session.toSessionJoinSuccess(): SessionJoinSuccess =
    SessionJoinSuccess(
        shareCode = this.shareCode.value,
        participantCount = ParticipantsRule.countActiveParticipants(participants),
    )

internal fun Session.toSessionLeaveSuccess(): SessionLeaveSuccess =
    SessionLeaveSuccess(
        shareCode = this.shareCode.value,
    )

internal fun Session.toStartSessionSuccess(): StartSessionSuccess =
    StartSessionSuccess(
        shareCode = shareCode.value,
        participantCount = ParticipantsRule.countActiveParticipants(participants),
    )

internal fun UpdateSessionGridDto.toCommand(actorId: UserId): SessionGridCommand =
    when (CommandType.valueOf(this.commandType)) {
        CommandType.PLACE_LETTER -> this.toSetLetterCommand(actorId)
        CommandType.ERASE_LETTER -> this.toClearCellCommand(actorId)
    }

private fun UpdateSessionGridDto.toSetLetterCommand(actorId: UserId): SessionGridCommand.SetLetter =
    SessionGridCommand.SetLetter(
        sessionId = SessionShareCode(this.shareCode),
        actorId = actorId,
        position = CellPos(this.posX, this.posY),
        letter = this.letter!!,
    )

private fun UpdateSessionGridDto.toClearCellCommand(actorId: UserId): SessionGridCommand.ClearCell =
    SessionGridCommand.ClearCell(
        sessionId = SessionShareCode(this.shareCode),
        actorId = actorId,
        position = CellPos(this.posX, this.posY),
    )

internal fun UpdateSessionGridDto.toSuccess(): SessionGridUpdateSuccess =
    SessionGridUpdateSuccess(
        shareCode = this.shareCode,
        posX = this.posX,
        posY = this.posY,
        letter = this.letter,
        commandType = this.commandType,
    )
