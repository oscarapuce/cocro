package com.cocro.application.session.mapper

import com.cocro.application.session.dto.DimensionDto
import com.cocro.application.session.dto.SessionSummaryDto
import com.cocro.domain.session.model.Session
import com.cocro.domain.session.rule.ParticipantsRule

fun Session.toSummary(role: String): SessionSummaryDto {
    val template = gridTemplate
    return SessionSummaryDto(
        sessionId = id.toString(),
        shareCode = shareCode.toString(),
        status = status.name,
        gridTitle = template?.title ?: "Sans titre",
        gridDimension = DimensionDto(
            width = template?.width ?: 0,
            height = template?.height ?: 0,
        ),
        authorName = author.username,
        participantCount = ParticipantsRule.countActiveParticipants(participants),
        role = role,
        createdAt = createdAt.toString(),
        updatedAt = updatedAt.toString(),
    )
}
