package com.cocro.domain.session.rule

object SessionLimitRule {
    const val MAX_ACTIVE_SESSIONS = 5

    fun canCreateOrJoin(activeCount: Int): Boolean = activeCount < MAX_ACTIVE_SESSIONS
}

