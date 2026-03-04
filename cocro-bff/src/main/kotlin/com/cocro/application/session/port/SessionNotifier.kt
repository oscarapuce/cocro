package com.cocro.application.session.port

import com.cocro.application.session.dto.notification.SessionEvent
import com.cocro.kernel.auth.model.valueobject.UserId
import com.cocro.kernel.session.model.valueobject.SessionShareCode

interface SessionNotifier {
    /** Broadcast an event to all subscribers of a session topic. */
    fun broadcast(shareCode: SessionShareCode, event: SessionEvent)

    /** Send a private event to a single connected user (via /user/queue/session). */
    fun notifyUser(userId: UserId, event: SessionEvent)
}

