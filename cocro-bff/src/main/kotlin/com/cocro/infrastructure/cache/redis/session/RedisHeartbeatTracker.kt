package com.cocro.infrastructure.cache.redis.session

import com.cocro.application.session.port.HeartbeatTracker
import com.cocro.domain.auth.model.valueobject.UserId
import com.cocro.domain.session.model.valueobject.SessionId
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.util.UUID

private val SESSION_TTL: Duration = Duration.ofHours(24)

/**
 * Redis implementation of [HeartbeatTracker].
 *
 * Key structure:
 *   session:{id}:heartbeat:active  → Redis Set of active userId strings
 *   session:{id}:heartbeat:away    → Redis Hash { userId → awayTimestamp (epoch ms) }
 */
@Component
class RedisHeartbeatTracker(
    private val redisTemplate: RedisTemplate<String, String>,
) : HeartbeatTracker {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun markActive(sessionId: SessionId, userId: UserId) {
        val sid = sessionId.value.toString()
        val uid = userId.toString()
        redisTemplate.opsForSet().add(activeKey(sid), uid)
        redisTemplate.expire(activeKey(sid), SESSION_TTL)
        redisTemplate.opsForHash<String, String>().delete(awayKey(sid), uid)
        logger.debug("User {} marked active in session {}", uid, sid)
    }

    override fun markAway(sessionId: SessionId, userId: UserId) {
        val sid = sessionId.value.toString()
        val uid = userId.toString()
        redisTemplate.opsForSet().remove(activeKey(sid), uid)
        redisTemplate.opsForHash<String, String>().put(
            awayKey(sid),
            uid,
            Instant.now().toEpochMilli().toString(),
        )
        redisTemplate.expire(awayKey(sid), SESSION_TTL)
        logger.debug("User {} marked away in session {}", uid, sid)
    }

    override fun remove(sessionId: SessionId, userId: UserId) {
        val sid = sessionId.value.toString()
        val uid = userId.toString()
        redisTemplate.opsForSet().remove(activeKey(sid), uid)
        redisTemplate.opsForHash<String, String>().delete(awayKey(sid), uid)
        logger.debug("User {} removed from heartbeat tracking in session {}", uid, sid)
    }

    override fun isAway(sessionId: SessionId, userId: UserId): Boolean {
        val sid = sessionId.value.toString()
        val uid = userId.toString()
        return redisTemplate.opsForHash<String, String>().hasKey(awayKey(sid), uid)
    }

    override fun getTimedOutUserIds(sessionId: SessionId, gracePeriodMs: Long): List<UserId> {
        val sid = sessionId.value.toString()
        val cutoff = Instant.now().toEpochMilli() - gracePeriodMs
        return redisTemplate.opsForHash<String, String>()
            .entries(awayKey(sid))
            .filter { (_, awayTimestamp) -> awayTimestamp.toLong() < cutoff }
            .map { (uid, _) -> UserId(UUID.fromString(uid)) }
    }

    private fun activeKey(sessionId: String) = "session:$sessionId:heartbeat:active"
    private fun awayKey(sessionId: String) = "session:$sessionId:heartbeat:away"
}
