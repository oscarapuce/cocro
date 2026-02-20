package com.cocro.application.session.port

import com.cocro.kernel.session.model.Session
import com.cocro.kernel.session.model.valueobject.SessionShareCode

interface SessionRepository {
    fun save(session: Session): Session

    fun findByShareCode(code: SessionShareCode): Session?

    fun existsByShareCode(code: SessionShareCode): Boolean
}
