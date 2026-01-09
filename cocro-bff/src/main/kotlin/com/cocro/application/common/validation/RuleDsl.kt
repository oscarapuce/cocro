package com.cocro.application.common.validation

class RuleDsl(
    private val presence: Presence,
    private val valueProvider: () -> Any?,
    private val action: () -> Unit,
) {
    fun run() {
        val value = valueProvider()
        when (presence) {
            Presence.REQUIRED -> action()
            Presence.OPTIONAL -> if (value != null) action()
        }
    }
}
