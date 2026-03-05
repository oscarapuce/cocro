package com.cocro.infrastructure.cache.redis.session

import com.cocro.application.session.port.SessionGridStateCache
import com.cocro.infrastructure.cache.redis.session.dto.SessionGridStateRedisDto
import com.cocro.infrastructure.cache.redis.session.dto.toDomain
import com.cocro.infrastructure.cache.redis.session.dto.toRedisDto
import com.cocro.kernel.session.model.state.SessionGridState
import com.cocro.kernel.session.model.valueobject.SessionId
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Component
import java.util.UUID
import java.util.concurrent.TimeUnit

private const val TTL_HOURS = 24L

/**
 * Redis implementation of [SessionGridStateCache].
 *
 * Key structure:
 *   session:{id}:state      → JSON of SessionGridStateRedisDto
 *   session:{id}:lastFlush  → Long (last flushed revision)
 *   sessions:active         → Redis Set of active sessionId strings
 */
@Component
class RedisSessionGridStateCache(
    private val redisTemplate: RedisTemplate<String, String>,
    private val objectMapper: ObjectMapper,
) : SessionGridStateCache {

    private val logger = LoggerFactory.getLogger(javaClass)

    // Atomic compareAndSet via Lua: checks revision, writes new state if matching.
    private val compareAndSetScript = DefaultRedisScript<Long>().apply {
        setScriptText(
            """
            local current = redis.call('GET', KEYS[1])
            if not current then
              return redis.error_reply('NOT_FOUND')
            end
            local state = cjson.decode(current)
            if tostring(state['revision']) ~= ARGV[1] then
              return redis.error_reply('CONFLICT')
            end
            redis.call('SET', KEYS[1], ARGV[2])
            redis.call('EXPIRE', KEYS[1], ARGV[3])
            return tonumber(ARGV[4])
            """.trimIndent(),
        )
        resultType = Long::class.java
    }

    override fun get(sessionId: SessionId): SessionGridState? {
        val json = redisTemplate.opsForValue().get(stateKey(sessionId)) ?: return null
        return objectMapper.readValue<SessionGridStateRedisDto>(json).toDomain()
    }

    override fun compareAndSet(
        sessionId: SessionId,
        expectedRevision: Long,
        newState: SessionGridState,
    ): Long {
        val newDto = newState.toRedisDto()
        val newJson = objectMapper.writeValueAsString(newDto)
        val newRevision = newState.revision.value

        val result = redisTemplate.execute(
            compareAndSetScript,
            listOf(stateKey(sessionId)),
            expectedRevision.toString(),
            newJson,
            TTL_HOURS.times(3600).toString(),
            newRevision.toString(),
        )

        checkNotNull(result) { "Redis compareAndSet returned null for session=${sessionId.value}" }
        return result
    }

    override fun initialize(sessionId: SessionId, state: SessionGridState) {
        val json = objectMapper.writeValueAsString(state.toRedisDto())
        redisTemplate.opsForValue().set(stateKey(sessionId), json, TTL_HOURS, TimeUnit.HOURS)
        redisTemplate.opsForValue().set(lastFlushKey(sessionId), "0")
        redisTemplate.opsForSet().add(ACTIVE_SESSIONS_KEY, sessionId.value.toString())
        logger.debug("Session {} initialized in Redis cache", sessionId.value)
    }

    override fun getLastFlushedRevision(sessionId: SessionId): Long =
        redisTemplate.opsForValue().get(lastFlushKey(sessionId))?.toLong() ?: 0L

    override fun markFlushed(sessionId: SessionId, revision: Long) {
        redisTemplate.opsForValue().set(lastFlushKey(sessionId), revision.toString())
    }

    override fun getActiveSessions(): Set<SessionId> =
        redisTemplate.opsForSet().members(ACTIVE_SESSIONS_KEY)
            .orEmpty()
            .map { SessionId(UUID.fromString(it)) }
            .toSet()

    private fun stateKey(sessionId: SessionId) = "session:${sessionId.value}:state"
    private fun lastFlushKey(sessionId: SessionId) = "session:${sessionId.value}:lastFlush"

    companion object {
        private const val ACTIVE_SESSIONS_KEY = "sessions:active"
    }
}
