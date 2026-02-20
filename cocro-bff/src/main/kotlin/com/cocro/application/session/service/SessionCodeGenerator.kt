package com.cocro.application.session.service

import com.cocro.application.common.service.AlphaNumCodeGenerator
import com.cocro.application.session.port.SessionRepository
import com.cocro.kernel.session.model.valueobject.SessionShareCode
import com.cocro.kernel.session.rule.SessionShareCodeRule
import org.springframework.stereotype.Component

@Component
class SessionCodeGenerator(
    private val sessionRepository: SessionRepository,
) : AlphaNumCodeGenerator<SessionShareCode>(size = SessionShareCodeRule.SESSION_SHARE_CODE_SIZE) {
    override fun wrap(raw: String): SessionShareCode = SessionShareCode(raw)

    override fun exists(candidate: SessionShareCode): Boolean = sessionRepository.existsByShareCode(candidate)
}
