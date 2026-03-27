package com.cocro.domain.session.model.state

@JvmInline
value class SessionGridStateRevision(
    val value: Long,
) {
    fun next(): SessionGridStateRevision = SessionGridStateRevision(value + 1)

    companion object {
        fun initial(): SessionGridStateRevision = SessionGridStateRevision(0)
    }
}
