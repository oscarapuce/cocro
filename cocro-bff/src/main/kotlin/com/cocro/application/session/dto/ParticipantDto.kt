package com.cocro.application.session.dto

data class ParticipantDto(
    val userId: String,
    val username: String,
    val status: String,
    val isCreator: Boolean,
)

