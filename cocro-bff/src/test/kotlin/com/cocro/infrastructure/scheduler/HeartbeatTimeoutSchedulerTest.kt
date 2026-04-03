package com.cocro.infrastructure.scheduler

import com.cocro.application.session.dto.notification.SessionEvent
import com.cocro.application.session.port.HeartbeatTracker
import com.cocro.application.session.port.SessionGridStateCache
import com.cocro.application.session.port.SessionNotifier
import com.cocro.application.session.port.SessionRepository
import com.cocro.domain.auth.model.valueobject.UserId
import com.cocro.domain.common.model.Author
import com.cocro.domain.grid.model.GridTemplateSnapshot
import com.cocro.domain.grid.model.valueobject.GridShareCode
import com.cocro.domain.session.model.Session
import com.cocro.domain.session.model.valueobject.SessionShareCode
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class HeartbeatTimeoutSchedulerTest {

    private val heartbeatTracker: HeartbeatTracker = mock()
    private val sessionGridStateCache: SessionGridStateCache = mock()
    private val sessionRepository: SessionRepository = mock()
    private val sessionNotifier: SessionNotifier = mock()
    private val gracePeriodMs = 30000L

    private val scheduler = HeartbeatTimeoutScheduler(
        heartbeatTracker, sessionGridStateCache, sessionRepository, sessionNotifier, gracePeriodMs,
    )

    private val gridId = GridShareCode("GRID01")
    private val snapshot = GridTemplateSnapshot(
        shortId = gridId, title = "T", width = 5, height = 5,
        difficulty = null, author = null, reference = null,
        description = null, globalClueLabel = null, globalClueWordLengths = null, cells = emptyList(),
    )

    private fun createSession(shareCode: String): Session {
        val author = Author(id = UserId.new(), username = "Creator")
        return Session.create(author = author, shareCode = SessionShareCode(shareCode), gridId = gridId, gridTemplate = snapshot)
    }

    @Test
    fun `should do nothing when no active sessions`() {
        whenever(sessionGridStateCache.getActiveSessions()).thenReturn(emptySet())
        scheduler.checkTimeouts()
        verifyNoInteractions(sessionRepository)
    }

    @Test
    fun `should do nothing when no users are timed out`() {
        val session = createSession("AB12")
        whenever(sessionGridStateCache.getActiveSessions()).thenReturn(setOf(session.id))
        whenever(heartbeatTracker.getTimedOutUserIds(session.id, gracePeriodMs)).thenReturn(emptyList())
        scheduler.checkTimeouts()
        verifyNoInteractions(sessionRepository)
    }

    @Test
    fun `should leave and remove timed out user and broadcast ParticipantLeft`() {
        val userId = UserId.new()
        val session = createSession("AB12").join(userId, "TimedOutUser")
        whenever(sessionGridStateCache.getActiveSessions()).thenReturn(setOf(session.id))
        whenever(heartbeatTracker.getTimedOutUserIds(session.id, gracePeriodMs)).thenReturn(listOf(userId))
        whenever(sessionRepository.findById(session.id)).thenReturn(session)
        whenever(sessionRepository.save(any())).thenAnswer { it.arguments[0] as Session }
        whenever(sessionGridStateCache.get(session.id)).thenReturn(null)

        scheduler.checkTimeouts()

        verify(heartbeatTracker).remove(session.id, userId)
        verify(sessionNotifier).broadcast(
            session.shareCode,
            SessionEvent.ParticipantLeft(userId = userId.toString(), username = "TimedOutUser", participantCount = 0, reason = "timeout"),
        )
    }

    @Test
    fun `should interrupt session when all participants time out`() {
        val userId = UserId.new()
        val session = createSession("AB12").join(userId, "OnlyUser")
        whenever(sessionGridStateCache.getActiveSessions()).thenReturn(setOf(session.id))
        whenever(heartbeatTracker.getTimedOutUserIds(session.id, gracePeriodMs)).thenReturn(listOf(userId))
        whenever(sessionRepository.findById(session.id)).thenReturn(session)
        whenever(sessionRepository.save(any())).thenAnswer { it.arguments[0] as Session }
        whenever(sessionGridStateCache.get(session.id)).thenReturn(null)

        scheduler.checkTimeouts()

        verify(sessionGridStateCache).deactivate(session.id)
        verify(sessionNotifier).broadcast(
            session.shareCode,
            SessionEvent.ParticipantLeft(userId = userId.toString(), username = "OnlyUser", participantCount = 0, reason = "timeout"),
        )
        verify(sessionNotifier).broadcast(
            session.shareCode,
            SessionEvent.SessionInterrupted(shareCode = session.shareCode.toString()),
        )
    }

    @Test
    fun `should skip when session not found in repository`() {
        val session = createSession("AB12")
        whenever(sessionGridStateCache.getActiveSessions()).thenReturn(setOf(session.id))
        whenever(heartbeatTracker.getTimedOutUserIds(session.id, gracePeriodMs)).thenReturn(listOf(UserId.new()))
        whenever(sessionRepository.findById(session.id)).thenReturn(null)

        scheduler.checkTimeouts()

        verifyNoInteractions(sessionNotifier)
    }
}
