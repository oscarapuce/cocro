package com.cocro.kernel.session.model.valueobject

import java.util.UUID

@JvmInline
value class SessionId(
    val value: UUID,
) {
    companion object {
        fun new(): SessionId = SessionId(UUID.randomUUID())

        fun from(raw: String): SessionId = SessionId(UUID.fromString(raw))
    }

    override fun toString(): String = value.toString()
}
