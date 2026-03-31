package com.cocro.infrastructure.persistence.mongo.session.document

data class ParticipantDocument(
    val userId: String,
    val status: String,
)
