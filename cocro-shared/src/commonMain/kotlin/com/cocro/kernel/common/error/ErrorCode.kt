package com.cocro.kernel.common.error

enum class ErrorCode(
    val message: String,
    val httpCode: Int,
) {
    // --- génériques ---
    INTERNAL_SERVER_ERROR("Internal server error", 500),
    UNAUTHORIZED("Unauthorized", 401),
    FORBIDDEN("Forbidden", 403),
    NOT_FOUND("Not found", 404),
    BAD_REQUEST("Bad request", 400),

    // --- grid domain ---
    GRID_TITLE_MISSING("Grid title is required", 400),
    GRID_DIFFICULTY_MISSING("Grid difficulty is required", 400),
    GRID_INVALID_CELL_COUNT("Grid size does not match cells count", 400),
    GRID_DUPLICATE_LETTER_HASH("A grid with the same letters already exists", 409),

    GRID_INVALID_LETTER("Invalid letter in grid cell", 400),
    GRID_INVALID_CLUE_COUNT("Too many clues in a cell", 400),
    GRID_DUPLICATE_CLUE_DIRECTION("Duplicate clue direction in a cell", 400),
    GRID_INVALID_SAFE_STRING("Invalid safe string", 400),

    // --- auth domain ---
    AUTH_USERNAME_ALREADY_EXISTS("Username already exists", 409),
    AUTH_INVALID_CREDENTIALS("Invalid username or password", 401),
    AUTH_USERNAME_INVALID("Invalid username", 400),
    AUTH_EMAIL_INVALID("Invalid email", 400),
    AUTH_PASSWORD_INVALID("Invalid password", 400),
    AUTH_PASSWORD_TOO_WEAK("Password is too weak", 400),
}
