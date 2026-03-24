package com.cocro.kernel.session.model.state

/**
 * Result of checking a [SessionGridState] against its reference [com.cocro.kernel.grid.model.Grid].
 *
 * - [isComplete] : every LetterCell in the reference grid has a value in the session state.
 * - [isCorrect]  : every filled value matches the reference letter (meaningful only when complete).
 * - [filledCount] / [totalCount] : progress indicator.
 */
data class GridCheckResult(
    val isComplete: Boolean,
    val isCorrect: Boolean,
    val filledCount: Int,
    val totalCount: Int,
    val wrongCount: Int,
)
