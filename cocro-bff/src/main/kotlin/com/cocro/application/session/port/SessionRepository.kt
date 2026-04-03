package com.cocro.application.session.port

import com.cocro.domain.auth.model.valueobject.UserId
import com.cocro.domain.grid.model.valueobject.GridShareCode
import com.cocro.domain.session.model.Session
import com.cocro.domain.session.model.state.SessionGridState
import com.cocro.domain.session.model.valueobject.SessionId
import com.cocro.domain.session.model.valueobject.SessionShareCode
import java.time.Instant

interface SessionRepository {
    fun save(session: Session): Session

    fun findByShareCode(code: SessionShareCode): Session?

    fun findById(sessionId: SessionId): Session?

    fun existsByShareCode(code: SessionShareCode): Boolean

    /** Partial update: persists only the grid state of an existing session. */
    fun updateGridState(sessionId: SessionId, gridState: SessionGridState)

    fun findByCreator(authorId: UserId): List<Session>

    fun findByParticipantUserId(userId: UserId): List<Session>

    fun deleteById(sessionId: SessionId)

    /** Count sessions where user is active (as author or JOINED participant) with status PLAYING or INTERRUPTED. */
    fun countActiveByUser(userId: UserId): Int

    /** Find all sessions with status INTERRUPTED and updatedAt before the given cutoff. */
    fun findInterruptedBefore(cutoff: Instant): List<Session>

    /** Check if any active (PLAYING or INTERRUPTED) session references this grid. */
    fun existsByGridIdAndActiveStatus(gridId: GridShareCode): Boolean
}
