package com.cocro.application.session.dto

data class CreateSessionDto(
    val gridId: String,
)

data class JoinSessionDto(
    val shareCode: String,
)

data class LeaveSessionDto(
    val shareCode: String,
)

data class UpdateSessionGridDto(
    val shareCode: String,
    val posX: Int,
    val posY: Int,
    val commandType: String,
    val letter: Char?,
)
