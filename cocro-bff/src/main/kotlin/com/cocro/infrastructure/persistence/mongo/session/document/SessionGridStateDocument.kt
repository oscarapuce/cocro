package com.cocro.infrastructure.persistence.mongo.session.document

import com.cocro.infrastructure.persistence.mongo.grid.document.CellDocument
import java.util.UUID

data class SessionGridStateDocument(
    val sessionId: UUID,
    val gridShortId: String,
    val revision: Long,
    val cells: List<CellDocument>,
)
