package com.cocro.application.session.validation

import com.cocro.application.session.dto.CreateSessionDto
import com.cocro.application.session.dto.JoinSessionDto
import com.cocro.application.session.dto.LeaveSessionDto
import com.cocro.application.session.dto.StartSessionDto
import com.cocro.application.session.dto.UpdateSessionGridDto
import com.cocro.kernel.grid.rule.GridShareCodeRule
import com.cocro.kernel.session.enum.CommandType
import com.cocro.kernel.session.error.SessionError
import com.cocro.kernel.session.rule.SessionShareCodeRule

internal fun validateCreateSessionDto(createSessionDto: CreateSessionDto): List<SessionError> {
    var errors = emptyList<SessionError>()
    if (!GridShareCodeRule.validate(createSessionDto.gridId)) {
        errors = errors + SessionError.InvalidGridId(createSessionDto.gridId)
    }
    return errors
}

internal fun validateJoinSessionDto(joinSessionDto: JoinSessionDto): List<SessionError> {
    var errors = emptyList<SessionError>()
    if (!SessionShareCodeRule.validate(joinSessionDto.shareCode)) {
        errors = errors + SessionError.InvalidShareCode(joinSessionDto.shareCode)
    }
    return errors
}

internal fun validateLeaveSessionDto(leaveSessionDto: LeaveSessionDto): List<SessionError> {
    var errors = emptyList<SessionError>()
    if (!SessionShareCodeRule.validate(leaveSessionDto.shareCode)) {
        errors = errors + SessionError.InvalidShareCode(leaveSessionDto.shareCode)
    }
    return errors
}

internal fun validateStartSessionDto(dto: StartSessionDto): List<SessionError> {
    var errors = emptyList<SessionError>()
    if (!SessionShareCodeRule.validate(dto.shareCode)) {
        errors = errors + SessionError.InvalidShareCode(dto.shareCode)
    }
    return errors
}

internal fun validateUpdateSessionGridDto(dto: UpdateSessionGridDto): List<SessionError> {
    var errors = emptyList<SessionError>()
    if (!SessionShareCodeRule.validate(dto.shareCode)) {
        errors = errors + SessionError.InvalidShareCode(dto.shareCode)
    }
    if (dto.posX < 0) {
        errors = errors + SessionError.InvalidPosition
    }
    if (dto.posY < 0) {
        errors = errors + SessionError.InvalidPosition
    }
    if (dto.commandType !in setOf(CommandType.PLACE_LETTER.name, CommandType.ERASE_LETTER.name)) {
        errors = errors + SessionError.InvalidCommand("commandType must be either PLACE_LETTER or ERASE_LETTER")
    }
    if (dto.commandType == CommandType.PLACE_LETTER.name && dto.letter == null) {
        errors = errors + SessionError.InvalidCommand("letter must be provided for PLACE_LETTER command")
    }
    if (dto.commandType == CommandType.ERASE_LETTER.name && dto.letter != null) {
        errors = errors + SessionError.InvalidCommand("letter must not be provided for ERASE_LETTER command")
    }
    return errors
}
