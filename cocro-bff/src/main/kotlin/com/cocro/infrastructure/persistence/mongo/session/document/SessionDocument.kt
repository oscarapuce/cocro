package com.cocro.infrastructure.persistence.mongo.session.document

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant
import java.util.UUID

@Document(collection = "grids")
@CompoundIndex(
    name = "shareCode_idx",
    def = "{'shareCode': 1}",
    unique = true,
)
data class SessionDocument(
    @Id
    val id: UUID,
    @Indexed(unique = true)
    val shareCode: String,
    val creatorId: String,
    val gridShortId: String,
    val status: String,
    val participants: Set<ParticipantDocument>,
    val sessionGridState: SessionGridStateDocument,
    val createdAt: Instant,
    val updatedAt: Instant,
)
