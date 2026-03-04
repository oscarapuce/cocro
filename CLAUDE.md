# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
# Build
./gradlew build
./gradlew cocro-bff:build
./gradlew cocro-shared:build

# Run BFF application
./gradlew cocro-bff:bootRun

# Test
./gradlew test
./gradlew cocro-bff:test
./gradlew cocro-shared:test

# Start local infrastructure (MongoDB + Redis via Docker/Podman)
bash scripts/compose-script.sh
```

## Architecture

This is a Kotlin/Spring Boot collaborative crossword app with two Gradle modules:

- **cocro-shared**: Kotlin multiplatform domain library (no framework dependencies). Contains all business logic, domain models, value objects, rules, and error types.
- **cocro-bff**: Spring Boot 3.2 backend-for-frontend with REST and WebSocket (STOMP).

### Layered Architecture (Clean/DDD)

```
cocro-shared (kernel)
└── domain: entities, value objects, sealed errors, CocroResult<T,E>

cocro-bff
├── application/    — use cases, DTOs, mappers, ports (interfaces)
├── infrastructure/ — MongoDB adapters, Redis cache, JWT, Spring Security
└── presentation/   — REST controllers, WebSocket controllers, error mapping
```

The application layer has **no Spring dependencies** — only ports/interfaces. Infrastructure implements those ports.

### Error Handling

Business operations return `CocroResult<T, E>` (a sealed `Success`/`Error` type defined in `cocro-shared`). Use cases return this type; controllers unwrap it and map errors to HTTP responses via `ErrorMapper`.

### Real-time (WebSocket)

- STOMP over WebSocket (`/ws` endpoint, configured in `WebSocketConfig`)
- `SessionWebSocketController` handles session commands
- `StompSessionNotifier` implements `SessionNotifier` port and broadcasts `SessionEvent`s
- Authentication on WebSocket connections via `StompAuthChannelInterceptor` and `CocroPrincipal`

### Key Domain Concepts

- **Grid**: Crossword grid with cells, clues, letters. Identified by `GridShareCode`.
- **Session**: A collaborative play session for a grid. Participants join via `SessionShareCode`. Session state is managed as `SessionGridState` with immutable command application (`SessionGridCommand`).
- **SessionGridState cache**: `InMemorySessionGridStateCache` implements `SessionGridStateCache` port (Redis-backed in production intent).

### Infrastructure

- **MongoDB 7**: Primary persistence. Documents in `infrastructure/persistence/mongo/`.
- **Redis 7**: Session grid state cache.
- **JWT (JJWT)**: Stateless auth. Claims include userId, username, role.
- **Spring Security**: JWT resource server + channel interceptor for WebSocket auth.
- **Dev credentials** (`.env`): `MONGO_USER=admin`, `MONGO_PASSWORD=admin`, `REDIS_PASSWORD=redispass`
