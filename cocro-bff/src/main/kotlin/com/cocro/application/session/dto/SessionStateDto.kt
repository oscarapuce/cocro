package com.cocro.application.session.dto

data class SessionStateDto(
    val sessionId: String,
    val shareCode: String,
    val revision: Long,
    /** cells as a list of { x, y, letter } for easy client-side consumption */
    val cells: List<CellStateDto>,
)

data class CellStateDto(
    val x: Int,
    val y: Int,
    val letter: Char,
)
