# Validation DSL Reference

## Why a Custom DSL

CoCro uses a custom validation DSL instead of Bean Validation (`@Valid`, `@NotNull`, etc.) for the following reasons:

- **Domain errors, not ConstraintViolations** — validation failures are `SessionError` / `GridError` instances, consistent with the `CocroResult` error model used everywhere
- **Composable and testable** — no Spring context required; validation is pure Kotlin
- **Domain-scoped rules** — validates by domain concept (shareCode, position, command) rather than generic constraints like `@Size` or `@Pattern`
- **Consistent error aggregation** — multiple validation failures are collected into a `List<DomainError>` and returned as a single `CocroResult.Error`

## DSL Structure (Session Example)

### Entry point

```kotlin
// ValidateSessionDtos.kt
fun validateJoinSessionDto(dto: JoinSessionDto): List<SessionError> =
    validateSession {
        shareCode(dto.shareCode) { required() }
    }

fun validateUpdateGridDto(dto: UpdateGridDto): List<SessionError> =
    validateSession {
        shareCode(dto.shareCode) { required() }
        position(dto.x, dto.y) { required(); inBounds() }
        command(dto.commandType, dto.letter) { required(); validLetter() }
    }
```

### Engine

```kotlin
// SessionValidationEngine.kt
internal class SessionValidationEngine {
    private val _errors = mutableListOf<SessionError>()
    val errors: List<SessionError> get() = _errors.toList()

    fun validateShareCode(value: String?) {
        if (value.isNullOrBlank()) _errors += SessionError.InvalidShareCode
    }

    fun validatePosition(x: Int?, y: Int?) {
        if (x == null || y == null) _errors += SessionError.InvalidPosition
    }

    fun validatePositionInBounds(x: Int, y: Int, width: Int, height: Int) {
        if (x < 0 || x >= width || y < 0 || y >= height) _errors += SessionError.PositionOutOfBounds
    }

    fun validateCommand(type: String?, letter: Char?) {
        if (type == null) _errors += SessionError.InvalidCommand
    }
    // ...
}
```

### DSL class — one method per domain concept

```kotlin
// SessionValidationDsl.kt
internal fun validateSession(block: SessionValidationDsl.() -> Unit): List<SessionError> {
    val engine = SessionValidationEngine()
    SessionValidationDsl(engine).block()
    return engine.errors
}

internal class SessionValidationDsl(private val engine: SessionValidationEngine) {
    fun shareCode(value: String?, block: ShareCodeDsl.() -> Unit) =
        ShareCodeDsl(value, engine).block()

    fun position(x: Int?, y: Int?, block: PositionDsl.() -> Unit) =
        PositionDsl(x, y, engine).block()

    fun command(type: String?, letter: Char?, block: CommandDsl.() -> Unit) =
        CommandDsl(type, letter, engine).block()
}
```

### Scopes — expose validation rules for a specific concept

```kotlin
// SessionDslScopes.kt
internal class ShareCodeDsl(val value: String?, val engine: SessionValidationEngine) {
    fun required() = engine.validateShareCode(value)
}

internal class PositionDsl(val x: Int?, val y: Int?, val engine: SessionValidationEngine) {
    fun required() = engine.validatePosition(x, y)
    fun inBounds(width: Int = 20, height: Int = 20) {
        if (x != null && y != null) engine.validatePositionInBounds(x, y, width, height)
    }
}

internal class CommandDsl(val type: String?, val letter: Char?, val engine: SessionValidationEngine) {
    fun required() = engine.validateCommand(type, letter)
    fun validLetter() = engine.validateLetter(letter)
}
```

## File Layout

```
application/session/validation/
  ValidateSessionDtos.kt        — public entry points (validateXxxDto functions)
  dsl/
    engine/
      SessionValidationEngine.kt  — collects errors, raw validation logic
      SessionValidationDsl.kt     — DSL entry point + top-level class
      SessionDslScopes.kt         — per-concept scope classes (ShareCodeDsl, etc.)
```

## Adding a New Validation

1. Add a rule method to `SessionValidationEngine` — pure logic, adds to `_errors`
2. Add the corresponding method to the relevant scope class in `SessionDslScopes.kt`
3. Call the scope method from the entry point function in `ValidateSessionDtos.kt`

Example — adding a `minLength` rule for share codes:

```kotlin
// 1. Engine
fun validateShareCodeMinLength(value: String, min: Int = 6) {
    if (value.length < min) _errors += SessionError.ShareCodeTooShort
}

// 2. Scope
internal class ShareCodeDsl(...) {
    fun required() = engine.validateShareCode(value)
    fun minLength(min: Int = 6) {
        if (value != null) engine.validateShareCodeMinLength(value, min)
    }
}

// 3. Entry point
fun validateJoinSessionDto(dto: JoinSessionDto): List<SessionError> =
    validateSession {
        shareCode(dto.shareCode) { required(); minLength() }
    }
```

## Grid Validation (Same Pattern)

`GridValidationDsl` / `GridValidationEngine` / `GridDslScopes` follow the identical pattern for grid DTOs. Entry points live in `application/grid/validation/ValidateGridDtos.kt`.
