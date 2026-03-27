package com.cocro.domain.common.error

enum class ErrorCode(
    val message: String,
    val httpCode: Int,
) {
    // --- génériques ---
    INTERNAL_SERVER_ERROR("Internal server error", 500),
    UNAUTHORIZED("Not authenticated", 401),
    FORBIDDEN("Forbidden", 403),
    NOT_FOUND("Not found", 404),
    BAD_REQUEST("Bad request", 400),

    // --- grid domain ---
    GRID_TITLE_MISSING("Grid title is required", 400),
    GRID_INVALID_TITLE("Invalid grid title", 400),
    GRID_NOT_FOUND("Grid not found", 404),
    GRID_UNAUTHORIZED_MODIFICATION("Unauthorized grid modification", 403),
    GRID_INVALID_CELL_COUNT("Grid size does not match cells count", 400),
    GRID_INVALID_GRID_ID("Invalid grid ID", 400),
    GRID_DUPLICATE_LETTER_HASH("A grid with the same letters already exists", 409),

    GRID_UNAUTHORIZED_CREATION("Unauthorized grid creation", 401),
    GRID_INVALID_LETTER("Invalid letter in grid cell", 400),
    GRID_INVALID_CLUE_COUNT("Too many clues in a cell", 400),
    GRID_DUPLICATE_CLUE_DIRECTION("Duplicate clue direction in a cell", 400),
    GRID_INVALID_SAFE_STRING("Invalid safe string", 400),
    GRID_GLOBAL_CLUE_LABEL_MISSING("Global clue label is required when word lengths are specified", 400),
    GRID_GLOBAL_CLUE_NO_WORDS("Global clue must have at least one word", 400),
    GRID_GLOBAL_CLUE_WORD_LENGTH_INVALID("Global clue word length must be at least 1", 400),
    GRID_GLOBAL_CLUE_LETTER_COUNT_MISMATCH("Global clue letter count does not match indexed cell count", 400),
    GRID_GLOBAL_CLUE_NUMBERING_INVALID("Indexed cell numbers must form a consecutive sequence starting at 1", 400),

    // --- grid state domain ---
    GRID_STATE_INVALID_LETTER("Invalid letter for grid state", 400),
    GRID_STATE_INVALID_POSITION("Invalid position in grid state", 400),
    GRID_STATE_NOT_INITIALIZED("Grid state not initialized", 400),

    // --- session domain ---
    SESSION_FULL("Session is full", 409),
    SESSION_NOT_FOUND("Session not found", 403),
    SESSION_INVALID_SHARE_CODE("Invalid session share code", 400),
    SESSION_CANNOT_CREATE_WHEN_UNAUTHORIZED("Cannot create (or join) session when not authenticated", 401),
    SESSION_INVALID_STATUS_FOR_ACTION("Invalid session status for this action", 400),
    SESSION_ALREADY_PARTICIPANT("User is already a participant in this session", 409),
    SESSION_USER_NOT_IN_SESSION("User is not a participant in this session", 403),
    SESSION_GRID_NOT_SELECTED("No grid selected for this session", 400),
    SESSION_INVALID_COMMAND("Invalid command", 400),
    SESSION_NOT_ENOUGH_PARTICIPANTS("At least one participant is required to start the session", 400),
    SESSION_REFERENCE_GRID_NOT_FOUND("Reference grid not found for this session", 404),
    SESSION_GRID_CONCURRENT_MODIFICATION("Grid state was modified concurrently; client must resync", 409),

    // --- auth domain ---
    AUTH_USERNAME_ALREADY_EXISTS("Username already exists", 409),
    AUTH_INVALID_CREDENTIALS("Invalid username or password", 401),
    AUTH_USERNAME_INVALID("Invalid username", 400),
    AUTH_EMAIL_INVALID("Invalid email", 400),
    AUTH_PASSWORD_INVALID("Invalid password", 400),
    AUTH_PASSWORD_TOO_WEAK("Password is too weak", 400),
}
