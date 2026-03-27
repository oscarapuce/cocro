# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
# Build
cd cocro-bff && ./gradlew build

# Run BFF application
cd cocro-bff && ./gradlew bootRun

# Test
cd cocro-bff && ./gradlew test
cd cocro-bff && TESTCONTAINERS_RYUK_DISABLED=true ./gradlew test --no-daemon

# Start local infrastructure (MongoDB + Redis via Docker/Podman)
bash scripts/compose-script.sh
```

## Architecture

This is a Kotlin/Spring Boot collaborative crossword app. The `cocro-bff` directory is a standalone Gradle project (has its own `settings.gradle.kts` and Gradle wrapper).

- **cocro-bff**: Spring Boot 3.2 backend-for-frontend with REST and WebSocket (STOMP). Contains all business logic in `kernel/` (domain models, value objects, rules, error types).

### Layered Architecture (Clean/DDD)

```
cocro-bff
├── kernel/         — domain entities, value objects, sealed errors, CocroResult<T,E>
├── application/    — use cases, DTOs, mappers, ports (interfaces)
├── infrastructure/ — MongoDB adapters, Redis cache, JWT, Spring Security
└── presentation/   — REST controllers, WebSocket controllers, error mapping
```

The application layer has **no Spring dependencies** — only ports/interfaces. Infrastructure implements those ports.

### Error Handling

Business operations return `CocroResult<T, E>` (a sealed `Success`/`Error` type defined in `kernel/`). Use cases return this type; controllers unwrap it and map errors to HTTP responses via `ErrorMapper`.

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

### Angular Frontend (cocro-angular)

Angular 20 standalone + Signals app with DDD 4-folder architecture:

```
cocro-angular/src/app/
├── domain/         — models, rules, services (pure functions, no Angular deps)
├── application/    — services (GridSelectorService), ports
├── infrastructure/ — adapters (HTTP services), auth, guards, DTOs
└── presentation/   — features (pages), shared components
```

- TSConfig path aliases: `@domain/*`, `@application/*`, `@infrastructure/*`, `@presentation/*`
- Build: `cd cocro-angular && npx ng build`
- Dev: `cd cocro-angular && npx ng serve` (port 4200)
- Design: "L'Atelier du Cruciverbiste" — beige/paper + forest green, Séyès notebook style
