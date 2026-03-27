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

## Hard Prerequisites

### Idempotent `POST /api/sessions/join`

The session-launch flow (`/grid/mine` → "Lancer une session" → `/play/:shareCode`) relies on `GridPlayerComponent.ngOnInit()` calling `POST /api/sessions/join` on page load. Currently this returns 409 if the user is already JOINED. **This must be fixed before the session-launch flow can work.**

**Required change:** `JoinSessionUseCase` must detect that the requesting user is already a `JOINED` participant and return `200 + SessionFullDto` instead of `SessionError.AlreadyParticipant`. The 409 path is only reached by third parties trying to join a full session.

This prerequisite is tracked in the v0.2.0 backlog but is **required for v0.1.0** of this feature.

---

## Architecture

### Approach chosen: Standalone hub (Option A)

New `/grid/mine` page added alongside existing routes. Lobby untouched. Editor extended to support load-by-id for edit mode.

---

## BFF Changes

### 1. `GridRepository` port — new method

```kotlin
fun findByAuthor(author: UserId): List<Grid>
```

The creator is stored at `Grid.metadata.author` (type `UserId`) → mapped to `GridMetadataDocument.author` (String). The Spring Data derived query method is `findByMetadataAuthor(author: String): List<GridDocument>`.

**Files to update:**
- `GridRepository.kt` (port interface) — add `findByAuthor`
- `SpringDataGridRepository.kt` — add `findByMetadataAuthor`
- `MongoGridRepositoryAdapter.kt` — implement `findByAuthor` by calling `springDataRepo.findByMetadataAuthor(author.toString()).map { it.toDomain() }`
- `GridDocument.kt` — add `@CompoundIndex(name = "metadata_author_idx", def = "{'metadata.author': 1}")`

Add a `@CompoundIndex` on `GridDocument` to index the nested `metadata.author` path. `@Indexed` on the embedded `GridMetadataDocument.author` field has no effect since embedded documents are not `@Document` classes.

```kotlin
// GridDocument.kt — add to existing @CompoundIndex list
@CompoundIndex(name = "metadata_author_idx", def = "{'metadata.author': 1}")
```

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
    val updatedAt: Instant,
)
```

### 3. `GetMyGridsUseCase`

`@PreAuthorize("hasAnyRole('PLAYER', 'ADMIN')")` on the controller endpoint guarantees the user is authenticated before this use case runs. `currentUserOrNull()!!` is safe here — a null return would mean Spring Security failed to reject an unauthenticated request, which is a framework bug.

```kotlin
@Service
class GetMyGridsUseCase(
    private val currentUserProvider: CurrentUserProvider,
    private val gridRepository: GridRepository,
) {
    fun execute(): CocroResult<List<GridSummaryDto>, GridError> {
        val user = currentUserProvider.currentUserOrNull()!!
        val grids = gridRepository.findByAuthor(user.userId)
        return CocroResult.Success(grids.map { it.toSummaryDto() })
    }
}
```

### 4. `GridFullDto` — response DTO for `GET /api/grids/{shortId}`

Returns enough data to re-hydrate the editor. Matches the Angular `Grid` interface field for field.

**Important:** The existing `CellDto` is the *write* shape (flat: `letter: String?`, `separator: SeparatorType?`) used when Angular submits grids to the BFF. The Angular *read* shape (`Cell`) nests the letter: `letter: { value, separator, number }`. The BFF must return the nested shape so `GridHttpAdapter.getGrid()` (which deserializes directly to `Grid`) works without any mapping.

Define dedicated read DTOs:

```kotlin
data class GridFullDto(
    val gridId: String,           // shortId
    val title: String,
    val width: Int,
    val height: Int,
    val difficulty: String,
    val description: String?,
    val reference: String?,
    val author: String,
    val cells: List<GridFullCellDto>,
    val globalClue: GlobalClueDto?,
)

data class GridFullCellDto(
    val x: Int,
    val y: Int,
    val type: CellType,           // serialized by Jackson as "LETTER", "CLUE_SINGLE", etc.
    val letter: GridFullLetterDto?,
    val clues: List<ClueDto>?,    // reuses existing ClueDto(direction, text)
)

data class GridFullLetterDto(
    val value: String,
    val separator: SeparatorType, // serialized by Jackson as "NONE", "LEFT", etc.
    val number: Int?,
)

data class GlobalClueDto(
    val label: String,
    val wordLengths: List<Int>,
)
```

### 5. `GetGridUseCase`

`GridShareCode` has no nullable factory method — its constructor throws `IllegalArgumentException` on invalid input. Use `GridShareCodeRule.validate()` to guard before constructing (same pattern as validation layers elsewhere):

```kotlin
@Service
class GetGridUseCase(
    private val gridRepository: GridRepository,
) {
    fun execute(shortId: String): CocroResult<GridFullDto, GridError> {
        if (!GridShareCodeRule.validate(shortId)) {
            return CocroResult.Error(listOf(GridError.InvalidGridId(shortId)))
        }
        val shareCode = GridShareCode(shortId)
        val grid = gridRepository.findByShortId(shareCode)
            ?: return CocroResult.Error(listOf(GridError.GridNotFound(shortId)))
        return CocroResult.Success(grid.toFullDto())
    }
}
```

Authorization: any authenticated user may read a grid (`isAuthenticated()`).

### 6. `GridController` — two new endpoints

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
  updatedAt: string;
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
3. `GridPlayerComponent.ngOnInit()` calls `POST /api/sessions/join` (now idempotent — see Hard Prerequisites)

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

> **Note on redirect change:** The default `/grid` route currently redirects to `create`. This spec changes it to `mine` — navigating to `/grid` will now land on the hub instead of the editor.

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
- `GetMyGridsUseCase`: returns empty list when no grids, returns only current user's grids (not other users')
- `GridController` GET /mine: 401 if unauthenticated, 200 with list
- `GridController` GET /{shortId}: 200 with data, 404 if not found, 400 if invalid shortId
- `JoinSessionUseCase` idempotent: 200+SessionFullDto if already JOINED (not 409)

### Angular
- `MyGridsComponent`: renders grid cards, shows empty state, handles error
- `GridEditorComponent` edit mode: loads existing grid, calls patchGrid on submit
- `GetMyGridsUseCase`: delegates to port correctly
