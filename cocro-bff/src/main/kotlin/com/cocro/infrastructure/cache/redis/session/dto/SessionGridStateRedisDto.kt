package com.cocro.infrastructure.cache.redis.session.dto

import com.cocro.kernel.grid.model.CellPos
import com.cocro.kernel.grid.model.valueobject.GridShareCode
import com.cocro.kernel.session.model.state.SessionGridCellState
import com.cocro.kernel.session.model.state.SessionGridState
import com.cocro.kernel.session.model.state.SessionGridStateRevision
import com.cocro.kernel.session.model.valueobject.SessionId
import java.util.UUID

/**
 * Redis-friendly representation of [SessionGridState].
 * Map<CellPos, SessionGridCellState> is flattened to Map<String, Char>
 * using "x,y" as key and the letter char as value.
 */
data class SessionGridStateRedisDto(
    val sessionId: String,
    val gridShareCode: String,
    val revision: Long,
    val cells: Map<String, Char>,
)

fun SessionGridState.toRedisDto() = SessionGridStateRedisDto(
    sessionId = sessionId.value.toString(),
    gridShareCode = gridShareCode.value,
    revision = revision.value,
    cells = cells.entries.associate { (pos, state) ->
        "${pos.x},${pos.y}" to (state as SessionGridCellState.Letter).value
    },
)

fun SessionGridStateRedisDto.toDomain() = SessionGridState(
    sessionId = SessionId(UUID.fromString(sessionId)),
    gridShareCode = GridShareCode(gridShareCode),
    revision = SessionGridStateRevision(revision),
    cells = cells.entries.associate { (key, letter) ->
        val (x, y) = key.split(",").map { it.toInt() }
        CellPos(x, y) to SessionGridCellState.Letter(letter)
    },
)
