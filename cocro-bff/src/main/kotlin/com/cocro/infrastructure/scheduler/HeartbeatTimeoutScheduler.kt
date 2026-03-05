package com.cocro.infrastructure.scheduler

import com.cocro.application.session.port.HeartbeatTracker
import com.cocro.application.session.port.SessionGridStateCache
import com.cocro.application.session.port.SessionNotifier
import com.cocro.application.session.port.SessionRepository
import com.cocro.application.session.dto.notification.SessionEvent
import com.cocro.kernel.session.rule.ParticipantsRule
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Periodically checks for users who disconnected (STOMP) and did not reconnect
 * within the grace period. Confirmed timed-out users are removed from the session
 * and a [SessionEvent.ParticipantLeft] is broadcast with reason "timeout".
 */
@Component
class HeartbeatTimeoutScheduler(
    private val heartbeatTracker: HeartbeatTracker,
    private val sessionGridStateCache: SessionGridStateCache,
    private val sessionRepository: SessionRepository,
    private val sessionNotifier: SessionNotifier,
    @Value("\${cocro.session.heartbeat.grace-period-ms:30000}") private val gracePeriodMs: Long,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${cocro.session.heartbeat.check-interval-ms:15000}")
    fun checkTimeouts() {
        val activeSessions = sessionGridStateCache.getActiveSessions()

        for (sessionId in activeSessions) {
            val timedOut = heartbeatTracker.getTimedOutUserIds(sessionId, gracePeriodMs)
            if (timedOut.isEmpty()) continue

            val session = sessionRepository.findById(sessionId) ?: continue

            var updatedSession = session
            for (userId in timedOut) {
                logger.info(
                    "User {} timed out in session {} — removing from session",
                    userId,
                    sessionId.value,
                )
                updatedSession = updatedSession.leave(userId)
                heartbeatTracker.remove(sessionId, userId)
                heartbeatTracker.unregisterUserSession(userId)
            }

            val saved = sessionRepository.save(updatedSession)

            // Flush grid state to Mongo when participants change
            sessionGridStateCache.get(sessionId)?.let { gridState ->
                sessionRepository.updateGridState(sessionId, gridState)
                sessionGridStateCache.markFlushed(sessionId, gridState.revision.value)
            }

            val activeCount = ParticipantsRule.countActiveParticipants(saved.participants)
            for (userId in timedOut) {
                sessionNotifier.broadcast(
                    saved.shareCode,
                    SessionEvent.ParticipantLeft(
                        userId = userId.toString(),
                        participantCount = activeCount,
                        reason = "timeout",
                    ),
                )
            }
        }
    }
}
