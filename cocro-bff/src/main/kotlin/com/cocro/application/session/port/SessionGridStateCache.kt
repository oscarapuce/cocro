package com.cocro.application.session.port

import com.cocro.kernel.session.model.state.SessionGridState
import com.cocro.kernel.session.model.valueobject.SessionId
import com.cocro.kernel.session.model.valueobject.SessionShareCode

interface SessionGridStateCache {

    fun get(sessionId: SessionId): SessionGridState?

    /**
     * Apply update only if revision matches.
     * Returns new revision or throws conflict.
     */
    fun compareAndSet(
        sessionId: SessionId,
        expectedRevision: Long,
        newState: SessionGridState
    ): Long

    fun initialize(sessionId: SessionId, state: SessionGridState)
}
