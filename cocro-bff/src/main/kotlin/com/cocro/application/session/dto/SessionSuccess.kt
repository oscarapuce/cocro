package com.cocro.application.session.dto

import com.cocro.application.session.dto.CellStateDto
import com.cocro.application.session.dto.GridTemplateDto

data class SessionCreationSuccess(
    val sessionId: String,
    val shareCode: String,
)

data class SessionJoinSuccess(
    val sessionId: String,
    val participantCount: Int,
)

data class SessionLeaveSuccess(
    val sessionId: String,
)

data class StartSessionSuccess(
    val sessionId: String,
    val participantCount: Int,
)

data class SessionGridUpdateSuccess(
    val sessionId: String,
    val commandType: String,
)

data class GridCheckSuccess(
    val shareCode: String,
    val isComplete: Boolean,
    val isCorrect: Boolean,
    val filledCount: Int,
    val totalCount: Int,
    val wrongCount: Int,
)

data class SessionFullDto(
    val sessionId: String,
    val shareCode: String,
    val status: String,
    val participantCount: Int,
    val topicToSubscribe: String,
    val gridTemplate: GridTemplateDto,
    val gridRevision: Long,
    val cells: List<CellStateDto>,
)
