package com.cocro.application.session.validation.dsl.engine

internal class ShareCodeDsl(
    private val engine: SessionValidationEngine,
    private val value: String?,
) {
    fun required() = engine.validateShareCode(value)
}

internal class SessionGridIdDsl(
    private val engine: SessionValidationEngine,
    private val value: String?,
) {
    fun required() = engine.validateGridId(value)
}

internal class PositionDsl(
    private val engine: SessionValidationEngine,
    private val x: Int,
    private val y: Int,
) {
    fun nonNegative() = engine.validatePosition(x, y)
}

internal class CommandDsl(
    private val engine: SessionValidationEngine,
    private val type: String?,
    private val letter: Char?,
) {
    fun valid() = engine.validateCommand(type, letter)
}
