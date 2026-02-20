package com.cocro.application.session.dto

data class CreateSessionDto(
    val gridId: String,
)

// data class SelectGridDto(
//    val sessionId: String,
//    val gridId: String,
// )

// data class InviteUserDto(
//    val sessionId: String,
//    val invitedUserId: String,
// )

data class JoinSessionDto(
    val shareCode: String,
)

data class LeaveSessionDto(
    val shareCode: String,
)

data class StartSessionDto(
    val shareCode: String,
)

data class UpdateSessionGridDto(
    val shareCode: String,
    val posX: Int,
    val posY: Int,
    val commandType: String,
    val letter: Char?,
)
