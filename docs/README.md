# CoCro Documentation

These files are designed to be referenced with `@` in Claude Code prompts to provide context for feature requests and corrections.

## Index

### BFF (Backend-for-Frontend)

| File | Description |
|------|-------------|
| `@docs/bff/architecture.md` | Full BFF layered architecture: project layout, CocroResult, use case anatomy, domain command pattern, error model, port/adapter pattern, naming conventions |
| `@docs/bff/validation-dsl.md` | Custom validation DSL used instead of Bean Validation: why it exists, structure, DSL scopes, engine, and how to extend it |
| `@docs/bff/persistence.md` | MongoDB document model: collections, document-to-domain mapping pattern, partial updates, GridDocument structure |
| `@docs/bff/cache.md` | Redis session grid state cache and heartbeat tracker: key schema, CAS pattern, flush threshold, TTLs |
| `@docs/bff/security.md` | JWT config, REST security rules, WebSocket/STOMP auth flow, anonymous auth, ExecutorChannelInterceptor rationale |

### Sessions

| File | Description |
|------|-------------|
| `@docs/sessions/lifecycle.md` | Session state machine, transitions, REST endpoints summary, WebSocket events, heartbeat and reconnection |
| `@docs/session-ddd.md` | Full domain model documentation: Session aggregate, SessionGridState, commands, errors (pre-existing) |

### Frontend

| File | Description |
|------|-------------|
| `@docs/frontend/angular.md` | Angular 20 frontend: stack, directory structure, auth signals, STOMP subscribe pattern, route guards, anonymous flow |
| `@docs/frontend/cmp.md` | Compose Multiplatform (KMP) mobile app: MVI architecture, navigation, state model, custom keyboard, networking, design system |

### Design

| File | Description |
|------|-------------|
| `@docs/design/option-2.md` | Approved design spec "L'Atelier du Cruciverbiste": palette, typography, component specs, layout wireframes for all screens |
| `@docs/design/catalog.html` | Visual component catalog (open in browser): swatches, typography specimens, buttons, fields, keyboard, grid cells, page previews |

### Infrastructure

| File | Description |
|------|-------------|
| `@docs/infra/overview.md` | VPS OVH specs, budget RAM, architecture générale, exposition réseau, accès dev SSH tunnel, distribution APK Android |
| `@docs/infra/kubernetes.md` | k3s setup, namespaces, workloads, Ingress Traefik, Secrets, PersistentVolumes, concepts k8s couverts |
