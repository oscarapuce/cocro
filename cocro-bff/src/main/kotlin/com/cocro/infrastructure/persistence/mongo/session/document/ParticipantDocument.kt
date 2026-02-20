package com.cocro.infrastructure.persistence.mongo.session.document

import java.util.UUID

data class ParticipantDocument(
    val userId: UUID,
    val status: String,
)
