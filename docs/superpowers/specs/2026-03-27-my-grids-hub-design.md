# My Grids Hub — Design Spec

**Date:** 2026-03-27
**Status:** Approved

---

## Goal

Connect the existing GridEditor to a usable flow: after creating or editing a grid, the user lands on a **"Mes grilles"** hub page where they can launch a session or edit a grid. This closes the gap between grid creation and session creation.

## Context

- `GridEditorComponent` at `/grid/create` is fully built (cell types, clues, letters, global clue, grid params).
- After submit today: toast + stays on editor. No redirect. No session launch.
- Lobby `create-session.component` has a manual text input for gridId — brittle UX.
- `GET /api/grids/{shortId}` is called by Angular's `GridHttpAdapter` but the BFF endpoint does not exist.
- `GET /api/grids/mine` does not exist anywhere.

## Scope (v0.1.0)

### Out of scope
- Grid deletion (v0.2.0 backlog)
- Join-by-shareCode from the hub (lobby stays for this)
- Session listing / "my sessions" page (v0.2.0 backlog)

---

## Architecture

### Approach chosen: Standalone hub (Option A)

New `/grid/mine` page added alongside existing routes. Lobby untouched. Editor extended to support load-by-id for edit mode.

---

## BFF Changes

### 1. `GridRepository` port — new method

```kotlin
fun findByCreatorId(creatorId: UserId): List<Grid>
```

Implemented in `MongoGridRepository` using a query by `creatorId` field on the `GridDocument`.

### 2. `GridSummaryDto`

New response DTO (application layer):

```kotlin
data class GridSummaryDto(
    val gridId: String,       // shortId / GridShareCode
    val title: String,
    val width: Int,
    val height: Int,
    val difficulty: String,
    val createdAt: Instant,
)
```

### 3. `GetMyGridsUseCase`

```kotlin
@Service
class GetMyGridsUseCase(
    private val currentUserProvider: CurrentUserProvider,
    private val gridRepository: GridRepository,
) {
    fun execute(): CocroResult<List<GridSummaryDto>, GridError> {
        val user = currentUserProvider.currentUserOrNull()
            ?: return CocroResult.Error(listOf(GridError.UnauthorizedGridCreation))
        val grids = gridRepository.findByCreatorId(user.userId)
        return CocroResult.Success(grids.map { it.toSummaryDto() })
    }
}
```

### 4. `GridController` — two new endpoints

```kotlin
@GetMapping("/mine")
@PreAuthorize("hasAnyRole('PLAYER', 'ADMIN')")
fun getMyGrids(): ResponseEntity<*> =
    getMyGridsUseCase.execute().toResponseEntity(HttpStatus.OK)

@GetMapping("/{shortId}")
@PreAuthorize("isAuthenticated()")
fun getGrid(@PathVariable shortId: String): ResponseEntity<*> =
    getGridUseCase.execute(shortId).toResponseEntity(HttpStatus.OK)
```

`GetGridUseCase` is a thin use case: find by shortId, map to a full response DTO, return 404 if not found.

### Response DTO for GET /api/grids/{shortId}

Returns enough data to re-hydrate the editor: all cells, clues, title, dimensions, global clue. Reuses or extends existing grid mapping logic.

---

## Angular Changes

### Domain model

```typescript
// domain/models/grid-summary.model.ts
export interface GridSummary {
  gridId: string;
  title: string;
  width: number;
  height: number;
  difficulty: string;
  createdAt: string;
}
```

### Port extension

```typescript
// application/ports/grid/grid.port.ts
export interface GridPort {
  getGrid(gridId: string): Observable<Grid>;
  getMyGrids(): Observable<GridSummary[]>;
  submitGrid(request: SubmitGridRequest): Observable<GridSubmitResponse>;
  patchGrid(request: PatchGridRequest): Observable<void>;
}
```

### Adapter

```typescript
// infrastructure/adapters/grid/grid-http.adapter.ts
getMyGrids(): Observable<GridSummary[]> {
  return this.http.get<GridSummary[]>(`${this.baseUrl}/mine`);
}
```

### Use case

```typescript
// application/use-cases/get-my-grids.use-case.ts
@Injectable({ providedIn: 'root' })
export class GetMyGridsUseCase {
  private readonly gridPort = inject(GRID_PORT);
  execute(): Observable<GridSummary[]> {
    return this.gridPort.getMyGrids();
  }
}
```

### New page: `MyGridsComponent`

**Route:** `/grid/mine`
**Guard:** `playerGuard`

Responsibilities:
- On init: call `GetMyGridsUseCase.execute()`
- Render a list of `GridCardComponent`
- Show empty state with CTA "Créer une première grille" → `/grid/create`
- Handle loading and error states

### New component: `GridCardComponent`

Inputs: `grid: GridSummary`
Outputs: `launchSession` EventEmitter, `edit` EventEmitter

Displays: title, dimensions (W×H), difficulty badge, creation date.

Two action buttons:
- **"Lancer une session"** → parent calls `CreateSessionUseCase` → navigate to `/play/{shareCode}`
- **"Éditer"** → `router.navigate(['/grid', grid.gridId, 'edit'])`

The session launch sequence in `MyGridsComponent`:
1. `CreateSessionUseCase.execute(gridId)` → `POST /api/sessions` → `{ shareCode }`
2. `router.navigate(['/play', shareCode])`
3. `GridPlayerComponent.ngOnInit()` calls `POST /api/sessions/join` (idempotent — see note below)

> **Note on idempotent join:** `POST /api/sessions/join` currently returns 409 if already JOINED. For the session-launch flow to work without a second call, either: (a) the BFF makes join idempotent (return 200+SessionFullDto if already JOINED), or (b) `GridPlayerComponent` handles 409 by falling back to `GET /state`. Option (a) is preferred and tracked separately.

### `GridEditorComponent` — edit mode

Add `ActivatedRoute` injection. On init, read `:gridId` param:
- If absent → create mode (existing behavior, `createEmptyGrid`)
- If present → edit mode: call `gridPort.getGrid(gridId)` → `selectorService.initGrid(loaded)`

The submit button behavior changes by mode:
- **Create mode:** calls `submitGrid` → on success → navigate to `/grid/mine`
- **Edit mode:** calls `patchGrid` → on success → navigate to `/grid/mine`

### Route changes

```typescript
// editor.routes.ts
export const EDITOR_ROUTES: Routes = [
  { path: 'create',          canActivate: [playerGuard], loadComponent: () => import('./grid-editor/grid-editor.component').then(m => m.GridEditorComponent) },
  { path: ':gridId/edit',    canActivate: [playerGuard], loadComponent: () => import('./grid-editor/grid-editor.component').then(m => m.GridEditorComponent) },
  { path: 'mine',            canActivate: [playerGuard], loadComponent: () => import('./my-grids/my-grids.component').then(m => m.MyGridsComponent) },
  { path: '',                redirectTo: 'mine', pathMatch: 'full' },
];
```

### Bug fix

`create-session.component.html` line 22: `routerLink="/grids/editor"` → `routerLink="/grid/create"`.

---

## Data Flow Summary

```
/grid/create  ──[submit success]──►  /grid/mine
/grid/mine    ──[Éditer]──►  /grid/:id/edit  ──[save]──►  /grid/mine
/grid/mine    ──[Lancer]──►  POST /api/sessions  ──►  /play/:shareCode
/play/:code   ──[init]──►  POST /api/sessions/join (idempotent)  ──►  SessionFullDto
```

---

## Error Handling

| Scenario | Behaviour |
|---|---|
| `GET /api/grids/mine` fails | Error state in `MyGridsComponent` with retry button |
| `POST /api/sessions` fails during "Lancer" | Toast error, stay on `/grid/mine` |
| `GET /api/grids/{id}` returns 404 in edit mode | Redirect to `/grid/mine` with toast "Grille introuvable" |
| `PATCH /api/grids` fails in edit mode | Toast error, stay in editor |

---

## Testing

### BFF
- `GetMyGridsUseCase`: returns empty list when no grids, returns only current user's grids
- `GridController` GET /mine: 401 if unauthenticated, 200 with list
- `GridController` GET /{shortId}: 200 with data, 404 if not found

### Angular
- `MyGridsComponent`: renders grid cards, shows empty state, handles error
- `GridEditorComponent` edit mode: loads existing grid, calls patchGrid on submit
- `GetMyGridsUseCase`: delegates to port correctly
