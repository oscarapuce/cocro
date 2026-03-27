package com.cocro.application.session.port

import com.cocro.domain.session.model.state.SessionGridState
import com.cocro.domain.session.model.valueobject.SessionId

interface SessionGridStateCache {

    fun get(sessionId: SessionId): SessionGridState?

    /**
     * Apply update only if revision matches.
     * Returns new revision or throws on conflict.
     */
    fun compareAndSet(
        sessionId: SessionId,
        expectedRevision: Long,
        newState: SessionGridState,
    ): Long

    /** Initialize cache for a new session. Also registers the session as active. */
    fun initialize(sessionId: SessionId, state: SessionGridState)

    /** Last revision that was persisted to MongoDB for this session. */
    fun getLastFlushedRevision(sessionId: SessionId): Long

    /** Mark a revision as flushed to MongoDB. */
    fun markFlushed(sessionId: SessionId, revision: Long)

    /** All session IDs currently tracked in cache. */
    fun getActiveSessions(): Set<SessionId>
}
