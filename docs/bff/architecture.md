# BFF Architecture Reference

## Project Layout

```
cocro-bff (Spring Boot 3.2)
  kernel/          — domain model, value objects, rules, errors (no framework deps)
    kernel/auth/     — User, UserId, AuthenticatedUser, AuthError
    kernel/grid/     — Grid aggregate, Cell sealed (LetterCell/ClueCell/BlackCell), Letter, GridShareCode
    kernel/session/  — Session aggregate, SessionGridState, SessionGridCommand, SessionError
    kernel/common/   — CocroResult<T,E>, CocroError, ErrorCode
  application/     — use cases, DTOs, mappers, ports (interfaces only — NO Spring deps)
  infrastructure/  — MongoDB adapters, Redis cache, JWT, Spring Security, schedulers
  presentation/    — REST controllers, WebSocket controllers, ErrorMapper
```

The `kernel` package has zero framework dependencies. All business logic, domain models, value objects, rules, and error types live here.

> **Note:** `cocro-shared` (Kotlin Multiplatform) was absorbed into `cocro-bff` as a plain Kotlin package during the session lifecycle refactoring. The module no longer exists as a separate Gradle project.

## CocroResult

The core error-handling type, defined in `cocro-shared`:

```kotlin
sealed class CocroResult<out T, out E> {
    data class Success<T>(val value: T) : CocroResult<T, Nothing>()
    data class Error<E>(val errors: List<E>) : CocroResult<Nothing, E>()
}
```

Use cases always return `CocroResult<SuccessDto, DomainError>`. Controllers call `.toResponseEntity(HttpStatus.OK)` which maps:
- `Success` → 200 (or the provided status) with the value as body
- `Error` → HTTP status derived from `errors.maxOf { it.errorCode.httpCode }` via `ErrorMapper`

## Use Case Anatomy

Every use case follows this exact structure:

1. **Auth guard** — `currentUserProvider.currentUserOrNull() ?: return Unauthorized`
2. **DTO validation** — `validateXxxDto(dto)` returns `List<DomainError>`, return early if non-empty
3. **Load aggregate** — via repository, return `NotFound` if absent
4. **Business rule checks** — participant status, session status, etc.
5. **Domain command** — `aggregate.apply(command)` returns `CocroResult<Aggregate, Error>`
6. **Persistence** — `repository.save(updatedAggregate)`
7. **Side effects** — cache update, heartbeat, notification
8. **Return** — `CocroResult.Success(successDto)`

Example skeleton:

```kotlin
@Service
class JoinSessionUseCase(
    private val currentUserProvider: CurrentUserProvider,
    private val sessionRepository: SessionRepository,
    private val sessionNotifier: SessionNotifier,
) {
    fun execute(dto: JoinSessionDto): CocroResult<JoinSessionSuccess, SessionError> {
        val user = currentUserProvider.currentUserOrNull()
            ?: return CocroResult.Error(listOf(SessionError.Unauthorized))

        val validationErrors = validateJoinSessionDto(dto)
        if (validationErrors.isNotEmpty()) return CocroResult.Error(validationErrors)

        val session = sessionRepository.findByShareCode(dto.shareCode)
            ?: return CocroResult.Error(listOf(SessionError.SessionNotFound))

        // business rules ...

        val updated = when (val result = session.apply(SessionLifecycleCommand.Join(user.userId))) {
            is CocroResult.Success -> result.value
            is CocroResult.Error -> return result
        }

        sessionRepository.save(updated)
        sessionNotifier.broadcast(updated.shareCode, SessionEvent.ParticipantJoined(...))

        return CocroResult.Success(JoinSessionSuccess(...))
    }
}
```

## Domain Command Pattern

Domain mutations never happen directly in use cases. They go through sealed `XxxCommand` types defined in `cocro-shared`. The aggregate's `apply()` function is a pure function — it returns a new aggregate (or error) without side effects.

```kotlin
// cocro-bff/kernel
sealed interface SessionLifecycleCommand {
    data class Join(val actorId: UserId) : SessionLifecycleCommand
    data class Leave(val actorId: UserId) : SessionLifecycleCommand
}

// Usage in use case:
session.apply(SessionLifecycleCommand.Join(user.userId))
```

Grid cell mutations use `SessionGridCommand` (separate sealed interface in `cocro-shared`).

## Error Model

`ErrorCode` enum in `cocro-shared` carries `(message: String, httpCode: Int)`. All `CocroError` subtypes delegate to an `ErrorCode`. `ErrorMapper.toResponseEntity()` automatically computes HTTP status from `errors.maxOf { it.errorCode.httpCode }`.

Example error definition:

```kotlin
// cocro-shared
enum class ErrorCode(val message: String, val httpCode: Int) {
    SESSION_NOT_FOUND("Session not found", 404),
    UNAUTHORIZED("Unauthorized", 401),
    SESSION_ALREADY_STARTED("Session already started", 409),
    // ...
}

sealed class SessionError(override val errorCode: ErrorCode) : CocroError {
    object SessionNotFound : SessionError(ErrorCode.SESSION_NOT_FOUND)
    object Unauthorized : SessionError(ErrorCode.UNAUTHORIZED)
    object AlreadyStarted : SessionError(ErrorCode.SESSION_ALREADY_STARTED)
    // ...
}
```

## Port / Adapter Pattern

Every external dependency (database, cache, notifier) has a port interface in `application/*/port/`. Infrastructure implementations in `infrastructure/` are the only Spring beans that implement them. Use cases depend only on port interfaces, making them fully testable with fakes.

```
application/session/port/
  SessionRepository          — findByShareCode, save, updateGridState
  SessionGridStateCache      — get, set, compareAndSet
  HeartbeatTracker           — markActive, markAway, getTimedOutUserIds
  SessionNotifier            — broadcast, sendToUser

infrastructure/persistence/mongo/
  MongoSessionRepositoryAdapter implements SessionRepository

infrastructure/cache/redis/
  RedisSessionGridStateCache implements SessionGridStateCache
  RedisHeartbeatTracker implements HeartbeatTracker

infrastructure/websocket/
  StompSessionNotifier implements SessionNotifier
```

## Naming Conventions

| Concept | Convention | Example |
|---------|-----------|---------|
| Use cases | `XxxUseCase`, single public `execute()` method | `JoinSessionUseCase` |
| Input DTOs | `XxxDto` | `JoinSessionDto` |
| Output DTOs | `XxxSuccess` | `JoinSessionSuccess` |
| Port interfaces | Named after abstraction (no `I` prefix) | `SessionRepository`, `SessionNotifier` |
| Errors | Domain object + what went wrong | `SessionNotFound`, `InvalidShareCode` |
| Mongo adapters | `Mongo` prefix | `MongoSessionRepositoryAdapter` |
| Spring Data repos | `SpringData` prefix | `SpringDataSessionRepository` |
| Document mappers | `XxxDocumentMapper` | `SessionDocumentMapper` |
