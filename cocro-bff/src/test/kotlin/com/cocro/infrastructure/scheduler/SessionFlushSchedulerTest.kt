package com.cocro.infrastructure.scheduler

import com.cocro.application.session.port.SessionGridStateCache
import com.cocro.application.session.port.SessionRepository
import com.cocro.domain.grid.model.valueobject.GridShareCode
import com.cocro.domain.session.model.state.SessionGridState
import com.cocro.domain.session.model.valueobject.SessionId
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class SessionFlushSchedulerTest {

    private val sessionGridStateCache: SessionGridStateCache = mock()
    private val sessionRepository: SessionRepository = mock()

    private val scheduler = SessionFlushScheduler(sessionGridStateCache, sessionRepository)

    @Test
    fun `should do nothing when no active sessions`() {
        whenever(sessionGridStateCache.getActiveSessions()).thenReturn(emptySet())
        scheduler.flushIdleSessions()
        verifyNoInteractions(sessionRepository)
    }

    @Test
    fun `should flush when revision is greater than lastFlushed`() {
        val sessionId = SessionId.new()
        val gridId = GridShareCode("GRID01")
        val state = SessionGridState.initial(sessionId, gridId)
        whenever(sessionGridStateCache.getActiveSessions()).thenReturn(setOf(sessionId))
        whenever(sessionGridStateCache.get(sessionId)).thenReturn(state)
        whenever(sessionGridStateCache.getLastFlushedRevision(sessionId)).thenReturn(-1L)

        scheduler.flushIdleSessions()

        verify(sessionRepository).updateGridState(sessionId, state)
        verify(sessionGridStateCache).markFlushed(sessionId, state.revision.value)
    }

    @Test
    fun `should skip when revision equals lastFlushed`() {
        val sessionId = SessionId.new()
        val gridId = GridShareCode("GRID01")
        val state = SessionGridState.initial(sessionId, gridId)
        whenever(sessionGridStateCache.getActiveSessions()).thenReturn(setOf(sessionId))
        whenever(sessionGridStateCache.get(sessionId)).thenReturn(state)
        whenever(sessionGridStateCache.getLastFlushedRevision(sessionId)).thenReturn(state.revision.value)

        scheduler.flushIdleSessions()

        verifyNoInteractions(sessionRepository)
    }

    @Test
    fun `should skip when cache returns null`() {
        val sessionId = SessionId.new()
        whenever(sessionGridStateCache.getActiveSessions()).thenReturn(setOf(sessionId))
        whenever(sessionGridStateCache.get(sessionId)).thenReturn(null)

        scheduler.flushIdleSessions()

        verifyNoInteractions(sessionRepository)
    }
}
