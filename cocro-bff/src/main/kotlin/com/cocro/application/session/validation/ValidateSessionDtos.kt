package com.cocro.application.session.validation

import com.cocro.application.session.dto.CreateSessionDto
import com.cocro.application.session.dto.JoinSessionDto
import com.cocro.application.session.dto.LeaveSessionDto
import com.cocro.application.session.dto.StartSessionDto
import com.cocro.application.session.dto.UpdateSessionGridDto
import com.cocro.application.session.validation.dsl.engine.validateSession
import com.cocro.kernel.session.error.SessionError

internal fun validateCreateSessionDto(dto: CreateSessionDto): List<SessionError> = validateSession {
    gridId(dto.gridId) { required() }
}

internal fun validateJoinSessionDto(dto: JoinSessionDto): List<SessionError> = validateSession {
    shareCode(dto.shareCode) { required() }
}

internal fun validateLeaveSessionDto(dto: LeaveSessionDto): List<SessionError> = validateSession {
    shareCode(dto.shareCode) { required() }
}

internal fun validateStartSessionDto(dto: StartSessionDto): List<SessionError> = validateSession {
    shareCode(dto.shareCode) { required() }
}

internal fun validateUpdateSessionGridDto(dto: UpdateSessionGridDto): List<SessionError> = validateSession {
    shareCode(dto.shareCode) { required() }
    position(dto.posX, dto.posY) { nonNegative() }
    command(dto.commandType, dto.letter) { valid() }
}
