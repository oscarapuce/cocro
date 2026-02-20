package com.cocro.infrastructure.persistence.mongo.session.document

import com.cocro.infrastructure.persistence.mongo.grid.document.CellDocument

data class SessionGridStateDocument(
    val sessionShareCode: String,
    val gridShortId: String,
    val revision: Long,
    val cells: List<CellDocument>,
)
