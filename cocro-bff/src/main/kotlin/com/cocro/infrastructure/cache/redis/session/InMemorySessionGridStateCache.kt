package com.cocro.infrastructure.cache.redis.session

import com.cocro.application.session.port.SessionGridStateCache
import com.cocro.domain.session.model.state.SessionGridState
import com.cocro.domain.session.model.valueobject.SessionId
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory fallback implementation of [SessionGridStateCache].
 * Used in tests — not registered as a Spring bean (Redis is the primary impl).
 */
class InMemorySessionGridStateCache : SessionGridStateCache {

    private val store = ConcurrentHashMap<String, SessionGridState>()
    private val lastFlush = ConcurrentHashMap<String, Long>()
    private val activeSessions = ConcurrentHashMap.newKeySet<String>()

    override fun get(sessionId: SessionId): SessionGridState? =
        store[sessionId.value.toString()]

    override fun initialize(sessionId: SessionId, state: SessionGridState) {
        val key = sessionId.value.toString()
        store[key] = state
        lastFlush[key] = 0L
        activeSessions.add(key)
    }

    override fun compareAndSet(
        sessionId: SessionId,
        expectedRevision: Long,
        newState: SessionGridState,
    ): Long {
        val key = sessionId.value.toString()
        val current = store[key]
        val currentRevision = current?.revision?.value ?: -1L
        check(currentRevision == expectedRevision) {
            "Revision conflict: expected $expectedRevision but was $currentRevision"
        }
        store[key] = newState
        return newState.revision.value
    }

    override fun getLastFlushedRevision(sessionId: SessionId): Long =
        lastFlush[sessionId.value.toString()] ?: 0L

    override fun markFlushed(sessionId: SessionId, revision: Long) {
        lastFlush[sessionId.value.toString()] = revision
    }

    override fun getActiveSessions(): Set<SessionId> =
        activeSessions.map { SessionId(UUID.fromString(it)) }.toSet()
}
