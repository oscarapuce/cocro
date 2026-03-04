package com.cocro.application.session.dto

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
