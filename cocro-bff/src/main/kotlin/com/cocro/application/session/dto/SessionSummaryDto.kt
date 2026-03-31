package com.cocro.application.session.dto

data class SessionSummaryDto(
    val sessionId: String,
    val shareCode: String,
    val status: String,
    val gridTitle: String,
    val gridDimension: DimensionDto,
    val authorName: String,
    val participantCount: Int,
    val role: String,
    val createdAt: String,
    val updatedAt: String,
)

data class DimensionDto(
    val width: Int,
    val height: Int,
)

