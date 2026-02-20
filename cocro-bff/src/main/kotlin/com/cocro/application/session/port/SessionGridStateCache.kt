package com.cocro.application.session.port

import com.cocro.kernel.session.model.state.SessionGridState
import com.cocro.kernel.session.model.valueobject.SessionShareCode

interface SessionGridStateCache {
    fun load(sessionCode: SessionShareCode): SessionGridState?

    fun save(state: SessionGridState)

    fun delete(sessionCode: SessionShareCode)
}
