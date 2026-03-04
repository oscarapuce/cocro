package com.cocro.infrastructure.cache.redis.session

import com.cocro.application.session.port.SessionGridStateCache
import com.cocro.kernel.session.model.state.SessionGridState
import com.cocro.kernel.session.model.valueobject.SessionId
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

@Component
class InMemorySessionGridStateCache : SessionGridStateCache {
    private val store = ConcurrentHashMap<String, SessionGridState>()

    override fun get(sessionId: SessionId): SessionGridState? =
        store[sessionId.value.toString()]

    override fun initialize(sessionId: SessionId, state: SessionGridState) {
        store[sessionId.value.toString()] = state
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
}
