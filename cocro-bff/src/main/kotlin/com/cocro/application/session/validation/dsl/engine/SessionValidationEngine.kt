package com.cocro.application.session.validation.dsl.engine

import com.cocro.kernel.grid.rule.GridShareCodeRule
import com.cocro.kernel.session.enum.CommandType
import com.cocro.kernel.session.error.SessionError
import com.cocro.kernel.session.rule.SessionShareCodeRule

internal class SessionValidationEngine {
    val errors = mutableListOf<SessionError>()

    fun validateShareCode(value: String?) {
        if (value == null || !SessionShareCodeRule.validate(value)) {
            errors += SessionError.InvalidShareCode(value)
        }
    }

    fun validateGridId(value: String?) {
        if (value == null || !GridShareCodeRule.validate(value)) {
            errors += SessionError.InvalidGridId(value)
        }
    }

    fun validatePosition(x: Int, y: Int) {
        if (x < 0 || y < 0) errors += SessionError.InvalidPosition
    }

    fun validateCommand(type: String?, letter: Char?) {
        val validTypes = setOf(CommandType.PLACE_LETTER.name, CommandType.ERASE_LETTER.name)
        if (type == null || type !in validTypes) {
            errors += SessionError.InvalidCommand("commandType must be either PLACE_LETTER or ERASE_LETTER")
            return
        }
        if (type == CommandType.PLACE_LETTER.name && letter == null) {
            errors += SessionError.InvalidCommand("letter must be provided for PLACE_LETTER command")
        }
        if (type == CommandType.ERASE_LETTER.name && letter != null) {
            errors += SessionError.InvalidCommand("letter must not be provided for ERASE_LETTER command")
        }
    }
}
