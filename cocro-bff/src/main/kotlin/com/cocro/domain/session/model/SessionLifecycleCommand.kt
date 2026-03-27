package com.cocro.domain.session.model

import com.cocro.domain.auth.model.valueobject.UserId

sealed interface SessionLifecycleCommand {
    data class Join(val actorId: UserId) : SessionLifecycleCommand
    data class Leave(val actorId: UserId) : SessionLifecycleCommand
}
