package com.cocro.kernel.session.model

import com.cocro.kernel.auth.model.valueobject.UserId

sealed interface SessionLifecycleCommand {
    val actorId: UserId

    data class Join(override val actorId: UserId) : SessionLifecycleCommand

    data class Leave(override val actorId: UserId) : SessionLifecycleCommand

    data class Start(override val actorId: UserId) : SessionLifecycleCommand
}
