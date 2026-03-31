package com.cocro.application.session.usecase

import com.cocro.application.session.dto.SessionSummaryDto
import com.cocro.application.session.mapper.toSummary
import com.cocro.application.session.port.SessionRepository
import com.cocro.domain.auth.model.valueobject.UserId
import org.springframework.stereotype.Service

@Service
class GetMySessionsUseCase(
    private val sessionRepository: SessionRepository,
) {
    fun execute(userId: UserId): List<SessionSummaryDto> {
        val created = sessionRepository.findByCreator(userId)
            .map { it.toSummary("CREATOR") }
        val joined = sessionRepository.findByParticipantUserId(userId)
            .filter { it.author.id != userId }
            .map { it.toSummary("PARTICIPANT") }
        return (created + joined).sortedByDescending { it.updatedAt }
    }
}

