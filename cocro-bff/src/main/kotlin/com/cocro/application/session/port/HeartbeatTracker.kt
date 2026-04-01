package com.cocro.application.session.port

import com.cocro.domain.auth.model.valueobject.UserId
import com.cocro.domain.session.model.valueobject.SessionId

interface HeartbeatTracker {

    /** Record or refresh a user as active in a session. */
    fun markActive(sessionId: SessionId, userId: UserId)

    /** Mark a user as away (STOMP disconnection, grace period starts). */
    fun markAway(sessionId: SessionId, userId: UserId)

    /** Remove a user entirely (explicit leave or timeout confirmed). */
    fun remove(sessionId: SessionId, userId: UserId)

    /** Returns true if the user is currently in "away" grace period for this session. */
    fun isAway(sessionId: SessionId, userId: UserId): Boolean

    /**
     * Returns users who have been "away" for longer than [gracePeriodMs].
     * These users should be considered as timed out.
     */
    fun getTimedOutUserIds(sessionId: SessionId, gracePeriodMs: Long): List<UserId>
}
