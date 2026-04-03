package com.cocro.infrastructure.scheduler

import com.cocro.application.session.port.HeartbeatTracker
import com.cocro.application.session.port.SessionGridStateCache
import com.cocro.application.session.port.SessionRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant

/**
 * Daily cleanup of INTERRUPTED sessions older than [expiryDays].
 * Hard-deletes expired sessions from MongoDB and cleans up Redis.
 */
@Component
class InterruptedSessionExpiryScheduler(
    private val sessionRepository: SessionRepository,
    private val sessionGridStateCache: SessionGridStateCache,
    private val heartbeatTracker: HeartbeatTracker,
    @Value("\${cocro.session.interrupted.expiry-days:7}") private val expiryDays: Long,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "0 0 3 * * *") // daily at 3 AM
    fun expireInterruptedSessions() {
        val cutoff = Instant.now().minus(Duration.ofDays(expiryDays))
        val expired = sessionRepository.findInterruptedBefore(cutoff)

        if (expired.isEmpty()) return

        logger.info("Found {} expired INTERRUPTED sessions (cutoff={})", expired.size, cutoff)

        for (session in expired) {
            session.participants.forEach { p ->
                heartbeatTracker.remove(session.id, p.userId)
            }
            sessionGridStateCache.deactivate(session.id)
            sessionRepository.deleteById(session.id)
            logger.info(
                "Expired INTERRUPTED session {} (last updated {})",
                session.shareCode.value,
                session.updatedAt,
            )
        }
    }
}

