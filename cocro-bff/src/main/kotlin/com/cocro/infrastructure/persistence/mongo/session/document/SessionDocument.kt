package com.cocro.infrastructure.persistence.mongo.session.document

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

@Document(collection = "sessions")
@CompoundIndexes(
    CompoundIndex(name = "shareCode_idx", def = "{'shareCode': 1}", unique = true),
    CompoundIndex(name = "authorId_idx", def = "{'authorId': 1}"),
    CompoundIndex(name = "participants_userId_idx", def = "{'participants.userId': 1}"),
)
data class SessionDocument(
    @Id
    val id: String,
    @Indexed(unique = true)
    val shareCode: String,
    val authorId: String,
    val authorUsername: String,
    val creatorId: String? = null,  // legacy fallback field
    val gridShortId: String,
    val gridTemplate: GridTemplateDocument?,
    val status: String,
    val participants: Set<ParticipantDocument>,
    val sessionGridState: SessionGridStateDocument,
    val createdAt: Instant,
    val updatedAt: Instant,
)
