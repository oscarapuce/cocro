package com.cocro.infrastructure.scheduler

import com.cocro.application.session.port.SessionGridStateCache
import com.cocro.application.session.port.SessionRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Periodically flushes idle sessions from Redis cache to MongoDB.
 * Runs on [cocro.session.flush.idle-check-ms] interval.
 * Flushes any session whose current revision hasn't been persisted yet.
 */
@Component
class SessionFlushScheduler(
    private val sessionGridStateCache: SessionGridStateCache,
    private val sessionRepository: SessionRepository,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${cocro.session.flush.idle-check-ms:60000}")
    fun flushIdleSessions() {
        val activeSessions = sessionGridStateCache.getActiveSessions()

        for (sessionId in activeSessions) {
            val state = sessionGridStateCache.get(sessionId) ?: continue
            val lastFlushed = sessionGridStateCache.getLastFlushedRevision(sessionId)

            if (state.revision.value > lastFlushed) {
                sessionRepository.updateGridState(sessionId, state)
                sessionGridStateCache.markFlushed(sessionId, state.revision.value)
                logger.debug(
                    "Idle flush: session={}, revision={} persisted to MongoDB",
                    sessionId.value,
                    state.revision.value,
                )
            }
        }
    }
}
