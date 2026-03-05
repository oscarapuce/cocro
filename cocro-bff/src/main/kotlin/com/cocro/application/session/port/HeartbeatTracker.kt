package com.cocro.application.session.port

import com.cocro.kernel.auth.model.valueobject.UserId
import com.cocro.kernel.session.model.valueobject.SessionId

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

    /** Persist the mapping userId → sessionId so disconnect events can find the session. */
    fun registerUserSession(userId: UserId, sessionId: SessionId)

    /** Look up which session a user belongs to (used on STOMP disconnect). */
    fun getSessionIdForUser(userId: UserId): SessionId?

    /** Remove the userId → sessionId mapping (on explicit leave or timeout). */
    fun unregisterUserSession(userId: UserId)
}
