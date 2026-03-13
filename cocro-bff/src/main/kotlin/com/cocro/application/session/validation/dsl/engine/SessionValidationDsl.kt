package com.cocro.application.session.validation.dsl.engine

import com.cocro.kernel.session.error.SessionError

internal class SessionValidationDsl(private val engine: SessionValidationEngine) {
    fun shareCode(value: String?, block: ShareCodeDsl.() -> Unit) = ShareCodeDsl(engine, value).block()

    fun gridId(value: String?, block: SessionGridIdDsl.() -> Unit) = SessionGridIdDsl(engine, value).block()

    fun position(x: Int, y: Int, block: PositionDsl.() -> Unit) = PositionDsl(engine, x, y).block()

    fun command(type: String?, letter: Char?, block: CommandDsl.() -> Unit) = CommandDsl(engine, type, letter).block()
}

internal fun validateSession(block: SessionValidationDsl.() -> Unit): List<SessionError> {
    val engine = SessionValidationEngine()
    SessionValidationDsl(engine).block()
    return engine.errors
}
