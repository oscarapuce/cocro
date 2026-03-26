package com.cocro.kernel.session.model

import com.cocro.kernel.auth.model.valueobject.UserId

sealed interface SessionLifecycleCommand {
    data class Join(val actorId: UserId) : SessionLifecycleCommand
    data class Leave(val actorId: UserId) : SessionLifecycleCommand
}
