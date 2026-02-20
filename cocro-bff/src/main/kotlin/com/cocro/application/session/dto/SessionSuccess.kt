package com.cocro.application.session.dto

data class SessionCreationSuccess(
    val shareCode: String,
)

data class SessionJoinSuccess(
    val shareCode: String,
    val participantCount: Int,
)

data class SessionLeaveSuccess(
    val shareCode: String,
)

data class StartSessionSuccess(
    val shareCode: String,
    val participantCount: Int,
)

data class SessionGridUpdateSuccess(
    val shareCode: String,
    val posX: Int,
    val posY: Int,
    val commandType: String,
    val letter: Char?,
)
