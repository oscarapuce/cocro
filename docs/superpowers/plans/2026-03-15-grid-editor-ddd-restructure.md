# Grid Editor DDD Restructure & Behavior Port

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restructure cocro-web into DDD 4-folder architecture, port grid editor behavior (selector, keyboard nav, business rules) from Cocro_old, fix the coral line.

**Architecture:** Move from flat `shared/` + `features/` to `domain/` + `application/` + `infrastructure/` + `presentation/`. Port domain models, cell/grid utilities, validation rules, and GridSelectorService from old frontend using modern Angular signals (not BehaviorSubject). Replace mutable cell operations with immutable ones.

**Tech Stack:** Angular 20, TypeScript 5.8, Signals, standalone components

---

## File Map

### New files to create
- `src/app/domain/models/grid.model.ts` — Rich domain types (Cell, Grid, Letter, Clue, Direction, etc.)
- `src/app/domain/services/cell-utils.ts` — Pure cell predicates + immutable cell mutations
- `src/app/domain/services/grid-utils.ts` — Grid operations (getCell, resizeGrid, createEmptyGrid, etc.)
- `src/app/domain/rules/grid.rules.ts` — Validation (isGridFullyFilled, isLetterFullyFilled, etc.)
- `src/app/application/services/grid-selector.service.ts` — Signal-based selector + keyboard nav
- `src/app/infrastructure/dto/grid.dto.ts` — API DTOs (SubmitGridRequest, etc.) + mappers

### Files to move (old path → new path)
- `shared/services/grid.service.ts` → `infrastructure/adapters/grid.service.ts`
- `shared/services/session.service.ts` → `infrastructure/adapters/session.service.ts`
- `shared/services/stomp.service.ts` → `infrastructure/adapters/stomp.service.ts`
- `shared/services/auth.service.ts` → `infrastructure/auth/auth.service.ts`
- `shared/interceptors/jwt.interceptor.ts` → `infrastructure/auth/jwt.interceptor.ts`
- `shared/guards/auth.guard.ts` → `infrastructure/guards/auth.guard.ts`
- `shared/guards/player.guard.ts` → `infrastructure/guards/player.guard.ts`
- `shared/models/auth.models.ts` → `domain/models/auth.model.ts`
- `shared/models/session.models.ts` → `domain/models/session.model.ts`
- `shared/models/session-events.models.ts` → `domain/models/session-events.model.ts`
- `shared/components/` → `presentation/shared/components/`
- `features/` → `presentation/features/`

### Files to delete after migration
- `shared/models/grid.models.ts` (replaced by domain + dto)
- `shared/` directory (empty after moves)

### Files to modify
- `tsconfig.json` — add path aliases (@domain, @application, @infrastructure, @presentation)
- `src/styles.scss` — fix coral line z-index
- `app.routes.ts` — update import paths
- `app.config.ts` — update import paths
- All moved files — update their internal import paths
- `presentation/features/grid-editor/editor/*` — refactor to use domain model + GridSelectorService

---

## Chunk 1: Coral Line Fix + TSConfig Paths

### Task 1: Fix coral line z-index

**Files:**
- Modify: `cocro-web/src/styles.scss:33-43`

- [ ] **Step 1: Fix z-index**

Change `z-index: 0` to `z-index: -1` on `body::before`:

```scss
body::before {
  content: '';
  position: fixed;
  top: 0;
  left: 72px;
  width: 1px;
  height: 100%;
  background: var(--color-margin-line);
  z-index: -1;
  pointer-events: none;
}
```

- [ ] **Step 2: Verify**

Run: `cd cocro-web && npx ng build --configuration=development 2>&1 | tail -5`
Expected: Build succeeds

### Task 2: Add TSConfig path aliases

**Files:**
- Modify: `cocro-web/tsconfig.json`

- [ ] **Step 1: Add paths to compilerOptions**

```json
"compilerOptions": {
  "baseUrl": ".",
  "paths": {
    "@domain/*": ["src/app/domain/*"],
    "@application/*": ["src/app/application/*"],
    "@infrastructure/*": ["src/app/infrastructure/*"],
    "@presentation/*": ["src/app/presentation/*"]
  },
  // ... existing options
}
```

- [ ] **Step 2: Verify build still passes**

Run: `cd cocro-web && npx ng build --configuration=development 2>&1 | tail -5`

---

## Chunk 2: Domain Layer

### Task 3: Create domain models

**Files:**
- Create: `src/app/domain/models/grid.model.ts`

- [ ] **Step 1: Create grid domain model**

Port from old frontend, using TypeScript string literal union types (idiomatic modern TS):

```typescript
export type CellType = 'LETTER' | 'CLUE_SINGLE' | 'CLUE_DOUBLE' | 'BLACK';
export type ClueDirection = 'RIGHT' | 'DOWN' | 'FROM_BELOW' | 'FROM_SIDE';
export type Direction = 'DOWNWARDS' | 'RIGHTWARDS' | 'NONE';
export type SeparatorType = 'LEFT' | 'UP' | 'BOTH' | 'NONE';
export type GridDifficulty = 'EASY' | 'MEDIUM' | 'HARD' | 'NONE';

export interface Clue {
  direction: ClueDirection;
  text: string;
}

export interface Letter {
  value: string;
  separator: SeparatorType;
  number?: number;
}

export interface Cell {
  x: number;
  y: number;
  type: CellType;
  letter?: Letter;
  clues?: Clue[];
}

export interface Grid {
  id: string;
  title: string;
  width: number;
  height: number;
  cells: Cell[];
  difficulty?: GridDifficulty;
  description?: string;
}
```

### Task 4: Create cell-utils (immutable)

**Files:**
- Create: `src/app/domain/services/cell-utils.ts`

- [ ] **Step 1: Create cell utility functions**

Port from old frontend but make all mutations **immutable** (return new objects):

```typescript
import { Cell, CellType, ClueDirection, Letter, SeparatorType } from '@domain/models/grid.model';

export const DEFAULT_LETTER: Letter = { value: '', separator: 'NONE' };

// Predicates
export function isCellClue(cell: Cell): boolean {
  return cell.type === 'CLUE_SINGLE' || cell.type === 'CLUE_DOUBLE';
}
export function isCellLetter(cell: Cell): boolean {
  return cell.type === 'LETTER';
}
export function isCellClueSingle(cell: Cell): boolean {
  return cell.type === 'CLUE_SINGLE';
}
export function isCellClueDouble(cell: Cell): boolean {
  return cell.type === 'CLUE_DOUBLE';
}
export function isCellBlack(cell: Cell): boolean {
  return cell.type === 'BLACK';
}

// Immutable mutations — all return NEW Cell
export function writeLetterInCell(cell: Cell, letter: string): Cell {
  if (!isCellLetter(cell)) return cell;
  const value = (letter.trim() ?? '').slice(0, 1).toUpperCase();
  return { ...cell, letter: { ...(cell.letter ?? { ...DEFAULT_LETTER }), value } };
}

export function writeNumberInCell(cell: Cell, num: number): Cell {
  if (!isCellLetter(cell)) return cell;
  return { ...cell, letter: { ...(cell.letter ?? { ...DEFAULT_LETTER }), number: num } };
}

export function setLetterInCell(cell: Cell): Cell {
  return { ...cell, type: 'LETTER', letter: { ...DEFAULT_LETTER }, clues: undefined };
}

export function setSingleClueInCell(cell: Cell): Cell {
  return { ...cell, type: 'CLUE_SINGLE', clues: [{ direction: 'RIGHT', text: '' }], letter: undefined };
}

export function setDoubleClueInCell(cell: Cell): Cell {
  return {
    ...cell,
    type: 'CLUE_DOUBLE',
    clues: [
      { direction: 'RIGHT', text: '' },
      { direction: 'DOWN', text: '' },
    ],
    letter: undefined,
  };
}

export function setBlackInCell(cell: Cell): Cell {
  return { ...cell, type: 'BLACK', clues: undefined, letter: undefined };
}

export function setSeparatorInCell(cell: Cell, separator: SeparatorType): Cell {
  if (!isCellLetter(cell)) return cell;
  return { ...cell, letter: { ...(cell.letter ?? { ...DEFAULT_LETTER }), separator } };
}
```

### Task 5: Create grid-utils

**Files:**
- Create: `src/app/domain/services/grid-utils.ts`

- [ ] **Step 1: Create grid utility functions**

Port from old frontend (already immutable):

```typescript
import { Cell, CellType, ClueDirection, Direction, Grid, SeparatorType } from '@domain/models/grid.model';
import { DEFAULT_LETTER } from '@domain/services/cell-utils';

export const MIN_GRID_WIDTH = 3;
export const MIN_GRID_HEIGHT = 3;
export const MAX_GRID_WIDTH = 50;
export const MAX_GRID_HEIGHT = 25;

export function getCell(grid: Grid, x: number, y: number): Cell | null {
  if (x < 0 || y < 0 || x >= grid.width || y >= grid.height) return null;
  const index = y * grid.width + x;
  return index < grid.cells.length ? grid.cells[index] : null;
}

export function withUpdatedCell(grid: Grid, cell: Cell): Grid {
  const index = cell.y * grid.width + cell.x;
  const newCells = [...grid.cells];
  newCells[index] = cell;
  return { ...grid, cells: newCells };
}

export function createEmptyGrid(id: string, title: string, width: number, height: number): Grid {
  return {
    id, title, width, height,
    cells: Array.from({ length: width * height }, (_, i) => ({
      x: i % width,
      y: Math.floor(i / width),
      type: 'LETTER' as CellType,
      letter: { value: '', separator: 'NONE' as SeparatorType },
    })),
  };
}

export function resizeGrid(grid: Grid, newWidth: number, newHeight: number): Grid {
  const newCells: Cell[] = [];
  for (let y = 0; y < newHeight; y++) {
    for (let x = 0; x < newWidth; x++) {
      if (x < grid.width && y < grid.height) {
        const oldCell = grid.cells[y * grid.width + x];
        newCells[y * newWidth + x] = { ...oldCell, x, y };
      } else {
        newCells[y * newWidth + x] = {
          x, y, type: 'LETTER', letter: { ...DEFAULT_LETTER },
        };
      }
    }
  }
  return { ...grid, width: newWidth, height: newHeight, cells: newCells };
}

export function isOutOfBounds(x: number, y: number, width: number, height: number): boolean {
  return x < 0 || x >= width || y < 0 || y >= height;
}

export function isValidSize(width: number, height: number): boolean {
  return width >= MIN_GRID_WIDTH && width <= MAX_GRID_WIDTH
    && height >= MIN_GRID_HEIGHT && height <= MAX_GRID_HEIGHT;
}

export function getDirectionFromSurroundingClue(cell: Cell, grid: Grid): Direction {
  if (cell.clues?.length) return 'NONE';

  const directions: { dx: number; dy: number; clueDir: ClueDirection[] }[] = [
    { dx: -1, dy: 0, clueDir: ['RIGHT', 'FROM_SIDE'] },
    { dx: 0, dy: -1, clueDir: ['DOWN', 'FROM_BELOW'] },
  ];

  for (const { dx, dy, clueDir } of directions) {
    const nx = cell.x + dx;
    const ny = cell.y + dy;
    if (nx < 0 || ny < 0 || nx >= grid.width || ny >= grid.height) continue;

    const adjacent = grid.cells[ny * grid.width + nx];
    if (!adjacent.clues?.length) continue;

    for (const clue of adjacent.clues) {
      if (clueDir.includes(clue.direction)) {
        if (clue.direction === 'DOWN' || clue.direction === 'FROM_SIDE') return 'DOWNWARDS';
        if (clue.direction === 'RIGHT' || clue.direction === 'FROM_BELOW') return 'RIGHTWARDS';
      }
    }
  }
  return 'NONE';
}
```

### Task 6: Create grid rules

**Files:**
- Create: `src/app/domain/rules/grid.rules.ts`

- [ ] **Step 1: Create validation rules**

```typescript
import { Cell, Clue, Grid, Letter } from '@domain/models/grid.model';
import { isCellClue, isCellLetter } from '@domain/services/cell-utils';

export function isLetterValid(letter: Letter | undefined): boolean {
  if (!letter) return false;
  return !!letter.value && /^[A-Z]$/.test(letter.value);
}

export function isClueValid(clue: Clue): boolean {
  return clue.text.trim().length > 0;
}

export function isCellValid(cell: Cell): boolean {
  if (isCellLetter(cell)) return isLetterValid(cell.letter);
  if (isCellClue(cell)) return cell.clues!.every(isClueValid);
  return true;
}

export function isGridFullyFilled(grid: Grid): boolean {
  return grid.cells.every(isCellValid);
}

export function isLetterFullyFilled(grid: Grid): boolean {
  return grid.cells.every(cell => {
    if (cell.clues && cell.clues.length > 0) return true;
    return isLetterValid(cell.letter);
  });
}
```

- [ ] **Step 2: Verify build**

Run: `cd cocro-web && npx ng build --configuration=development 2>&1 | tail -5`

---

## Chunk 3: Application Layer (GridSelectorService)

### Task 7: Create GridSelectorService with signals

**Files:**
- Create: `src/app/application/services/grid-selector.service.ts`

- [ ] **Step 1: Implement signal-based selector**

Port from old GridSelectorService, replacing BehaviorSubject with signals and using immutable cell operations:

```typescript
import { computed, Injectable, signal } from '@angular/core';
import { Cell, CellType, Direction, Grid } from '@domain/models/grid.model';
import {
  DEFAULT_LETTER,
  isCellLetter,
  setBlackInCell,
  setDoubleClueInCell,
  setLetterInCell,
  setSingleClueInCell,
  writeLetterInCell,
} from '@domain/services/cell-utils';
import {
  createEmptyGrid,
  getCell,
  getDirectionFromSurroundingClue,
  isOutOfBounds,
  isValidSize,
  resizeGrid,
  withUpdatedCell,
} from '@domain/services/grid-utils';

@Injectable({ providedIn: 'root' })
export class GridSelectorService {
  readonly grid = signal<Grid>(createEmptyGrid('0', '', 10, 10));
  readonly selectedX = signal(0);
  readonly selectedY = signal(0);
  readonly direction = signal<Direction>('NONE');

  readonly selectedCell = computed(() =>
    getCell(this.grid(), this.selectedX(), this.selectedY()),
  );

  /** Compute 2D rows for template rendering */
  readonly rows = computed(() => {
    const g = this.grid();
    const result: Cell[][] = [];
    for (let y = 0; y < g.height; y++) {
      result.push(g.cells.slice(y * g.width, (y + 1) * g.width));
    }
    return result;
  });

  initGrid(grid: Grid): void {
    this.grid.set(grid);
    this.selectedX.set(0);
    this.selectedY.set(0);
    this.direction.set('NONE');
  }

  selectOnClick(x: number, y: number): void {
    const g = this.grid();
    const cell = getCell(g, x, y);
    if (!cell) return;

    const dir = getDirectionFromSurroundingClue(cell, g);
    if (dir !== 'NONE') {
      this.direction.set(dir);
    }

    if (this.selectedX() === x && this.selectedY() === y) {
      this.inverseDirection();
    } else {
      this.select(x, y);
    }
  }

  moveUp(): void { this.move(0, -1); }
  moveDown(): void { this.move(0, 1); }
  moveLeft(): void { this.move(-1, 0); }
  moveRight(): void { this.move(1, 0); }

  inputLetter(letter: string): void {
    const cell = this.selectedCell();
    if (!cell || !isCellLetter(cell)) return;

    const updated = writeLetterInCell(cell, letter);
    this.grid.update(g => withUpdatedCell(g, updated));
    this.goToNextCell();
  }

  handleBackspace(): void {
    this.eraseLetter();
    this.goToNextCell(false);
  }

  handleDelete(): void {
    this.eraseLetter();
  }

  handleShift(): void {
    this.inverseDirection();
  }

  onCellTypeChange(type: CellType): void {
    const cell = this.selectedCell();
    if (!cell) return;

    let updated: Cell;
    switch (type) {
      case 'LETTER': updated = setLetterInCell(cell); break;
      case 'CLUE_SINGLE': updated = setSingleClueInCell(cell); break;
      case 'CLUE_DOUBLE': updated = setDoubleClueInCell(cell); break;
      case 'BLACK': updated = setBlackInCell(cell); break;
    }
    this.grid.update(g => withUpdatedCell(g, updated));
  }

  onResize(newWidth: number, newHeight: number): void {
    if (!isValidSize(newWidth, newHeight)) return;
    this.grid.update(g => resizeGrid(g, newWidth, newHeight));

    if (this.selectedX() >= newWidth) this.selectedX.set(newWidth - 1);
    if (this.selectedY() >= newHeight) this.selectedY.set(newHeight - 1);
  }

  updateCellInGrid(updatedCell: Cell): void {
    this.grid.update(g => withUpdatedCell(g, updatedCell));
  }

  // --- Private ---

  private select(x: number, y: number): void {
    const g = this.grid();
    if (!getCell(g, x, y)) return;
    this.selectedX.set(x);
    this.selectedY.set(y);
  }

  private inverseDirection(): void {
    const d = this.direction();
    if (d === 'DOWNWARDS') this.direction.set('RIGHTWARDS');
    else if (d === 'RIGHTWARDS') this.direction.set('DOWNWARDS');
  }

  private move(dx: number, dy: number): void {
    const g = this.grid();
    const nx = this.selectedX() + dx;
    const ny = this.selectedY() + dy;
    if (isOutOfBounds(nx, ny, g.width, g.height)) return;
    if (!getCell(g, nx, ny)) return;
    this.select(nx, ny);
  }

  private goToNextCell(isGoingForward = true): void {
    const dir = this.direction();
    const g = this.grid();
    if (dir === 'NONE') return;

    const dx = dir === 'RIGHTWARDS' ? (isGoingForward ? 1 : -1) : 0;
    const dy = dir === 'DOWNWARDS' ? (isGoingForward ? 1 : -1) : 0;

    let x = this.selectedX();
    let y = this.selectedY();
    const maxSteps = Math.max(g.width, g.height);
    let steps = 0;

    while (steps++ < maxSteps) {
      const nx = x + dx;
      const ny = y + dy;
      if (isOutOfBounds(nx, ny, g.width, g.height)) break;

      const next = getCell(g, nx, ny);
      if (!next) break;

      if (isCellLetter(next)) {
        this.select(nx, ny);
        break;
      }
      x = nx;
      y = ny;
    }
  }

  private eraseLetter(): void {
    const cell = this.selectedCell();
    if (!cell || !isCellLetter(cell)) return;

    const updated = writeLetterInCell(cell, '');
    this.grid.update(g => withUpdatedCell(g, updated));
  }
}
```

- [ ] **Step 2: Verify build**

Run: `cd cocro-web && npx ng build --configuration=development 2>&1 | tail -5`

---

## Chunk 4: DDD File Restructure

### Task 8: Move models to domain

**Files:**
- Move: `shared/models/auth.models.ts` → `domain/models/auth.model.ts`
- Move: `shared/models/session.models.ts` → `domain/models/session.model.ts`
- Move: `shared/models/session-events.models.ts` → `domain/models/session-events.model.ts`
- Create: `infrastructure/dto/grid.dto.ts` (extract DTOs from `shared/models/grid.models.ts`)

- [ ] **Step 1: Move auth, session, session-events models**

```bash
cd cocro-web/src/app
mkdir -p domain/models
mv shared/models/auth.models.ts domain/models/auth.model.ts
mv shared/models/session.models.ts domain/models/session.model.ts
mv shared/models/session-events.models.ts domain/models/session-events.model.ts
```

- [ ] **Step 2: Create grid DTOs in infrastructure**

Create `infrastructure/dto/grid.dto.ts` with the API-facing types from `shared/models/grid.models.ts`, plus mappers to/from domain Cell:

```typescript
import { Cell, CellType, ClueDirection, GridDifficulty } from '@domain/models/grid.model';

export interface ClueDto {
  direction: ClueDirection;
  text: string;
}

export interface CellDto {
  x: number;
  y: number;
  type: CellType;
  letter?: string;
  number?: number;
  clues?: ClueDto[];
}

export interface SubmitGridRequest {
  gridId?: string;
  title: string;
  difficulty: GridDifficulty;
  reference?: string;
  description?: string;
  width: number;
  height: number;
  cells: CellDto[];
}

export interface PatchGridRequest {
  gridId: string;
  title?: string;
  difficulty?: GridDifficulty;
  reference?: string;
  description?: string;
  width?: number;
  height?: number;
  cells?: CellDto[];
}

export interface GridSubmitResponse {
  gridId: string;
}

// Mappers
export function cellToDto(cell: Cell): CellDto {
  return {
    x: cell.x,
    y: cell.y,
    type: cell.type,
    letter: cell.letter?.value || undefined,
    number: cell.letter?.number,
    clues: cell.clues?.length ? cell.clues : undefined,
  };
}
```

- [ ] **Step 3: Delete old grid.models.ts**

```bash
rm cocro-web/src/app/shared/models/grid.models.ts
```

### Task 9: Move services to infrastructure

**Files:**
- Move: `shared/services/grid.service.ts` → `infrastructure/adapters/grid.service.ts`
- Move: `shared/services/session.service.ts` → `infrastructure/adapters/session.service.ts`
- Move: `shared/services/stomp.service.ts` → `infrastructure/adapters/stomp.service.ts`
- Move: `shared/services/auth.service.ts` → `infrastructure/auth/auth.service.ts`

- [ ] **Step 1: Move files**

```bash
cd cocro-web/src/app
mkdir -p infrastructure/adapters infrastructure/auth
mv shared/services/grid.service.ts infrastructure/adapters/grid.service.ts
mv shared/services/session.service.ts infrastructure/adapters/session.service.ts
mv shared/services/stomp.service.ts infrastructure/adapters/stomp.service.ts
mv shared/services/auth.service.ts infrastructure/auth/auth.service.ts
```

- [ ] **Step 2: Update imports in moved files**

- `grid.service.ts`: change `grid.models` import to `@infrastructure/dto/grid.dto`
- `session.service.ts`: change `session.models` import to `@domain/models/session.model`
- `stomp.service.ts`: change `session-events.models` import to `@domain/models/session-events.model`
- `auth.service.ts`: change `auth.models` import to `@domain/models/auth.model`

### Task 10: Move guards and interceptor

**Files:**
- Move: `shared/guards/auth.guard.ts` → `infrastructure/guards/auth.guard.ts`
- Move: `shared/guards/player.guard.ts` → `infrastructure/guards/player.guard.ts`
- Move: `shared/interceptors/jwt.interceptor.ts` → `infrastructure/auth/jwt.interceptor.ts`

- [ ] **Step 1: Move files**

```bash
cd cocro-web/src/app
mkdir -p infrastructure/guards
mv shared/guards/auth.guard.ts infrastructure/guards/auth.guard.ts
mv shared/guards/player.guard.ts infrastructure/guards/player.guard.ts
mv shared/interceptors/jwt.interceptor.ts infrastructure/auth/jwt.interceptor.ts
```

- [ ] **Step 2: Update imports in moved files**

Guards import AuthService: change to `@infrastructure/auth/auth.service`
JWT interceptor imports AuthService: change to `@infrastructure/auth/auth.service`

### Task 11: Move presentation files

**Files:**
- Move: `features/` → `presentation/features/`
- Move: `shared/components/` → `presentation/shared/components/`

- [ ] **Step 1: Move directories**

```bash
cd cocro-web/src/app
mkdir -p presentation/shared
mv features presentation/features
mv shared/components presentation/shared/components
```

- [ ] **Step 2: Clean up empty shared directory**

```bash
rm -rf cocro-web/src/app/shared
```

### Task 12: Update all import paths

- [ ] **Step 1: Update app.routes.ts**

All `./features/` imports → `@presentation/features/`
All `./shared/guards/` → `@infrastructure/guards/`

- [ ] **Step 2: Update app.config.ts**

`./shared/interceptors/jwt.interceptor` → `@infrastructure/auth/jwt.interceptor`

- [ ] **Step 3: Update all feature component imports**

Every file in `presentation/features/` that imports from `../shared/services/`, `../shared/models/`, `../shared/components/` needs path updates to use `@domain/`, `@infrastructure/`, `@presentation/shared/`.

Key files to update:
- `auth/login/login.component.ts`
- `auth/register/register.component.ts`
- `home/home.component.ts`
- `landing/landing.component.ts`
- `lobby/create/create-session.component.ts`
- `lobby/room/lobby-room.component.ts`
- `game/board/game-board.component.ts`
- `grid-editor/editor/grid-editor.component.ts`
- `grid-editor/editor/cell-editor-panel.component.ts`
- `grid-editor/editor/editor-cell.component.ts`

- [ ] **Step 4: Update spec files**

```
shared/services/auth.service.spec.ts → delete or move to infrastructure/auth/
shared/guards/auth.guard.spec.ts → delete or move to infrastructure/guards/
shared/guards/player.guard.spec.ts → delete or move to infrastructure/guards/
shared/interceptors/jwt.interceptor.spec.ts → delete or move to infrastructure/auth/
```

- [ ] **Step 5: Verify build**

Run: `cd cocro-web && npx ng build --configuration=development 2>&1 | tail -20`
Expected: 0 errors

---

## Chunk 5: Grid Editor Refactoring

### Task 13: Refactor GridEditorComponent to use GridSelectorService

**Files:**
- Modify: `presentation/features/grid-editor/editor/grid-editor.component.ts`
- Modify: `presentation/features/grid-editor/editor/grid-editor.component.html`

- [ ] **Step 1: Rewrite grid-editor.component.ts**

Replace `EditorCell[][]` grid with `GridSelectorService`. Remove `EditorCell` interface (use domain `Cell`). Remove internal grid state — delegate to selector.

Key changes:
- Inject `GridSelectorService` instead of managing grid state locally
- `metaForm` width/height changes → call `selector.onResize()`
- `onCellClick` → call `selector.selectOnClick(x, y)` when tool is SELECT, or `selector.onCellTypeChange()` when tool is a cell type
- Remove `buildGrid`, `resizeGrid`, `updateCell`, `getCell` — handled by domain/selector
- Remove `EditorCell` and `EditorTool` types — use `Cell` and `CellType | 'SELECT'`
- Save maps domain `Cell` to `CellDto` via `cellToDto` mapper

- [ ] **Step 2: Rewrite grid-editor.component.html**

Key changes:
- Grid rendering uses `selector.rows()` computed signal
- Each cell renders via `<app-editor-cell>` with `[cell]="cell"` using domain `Cell`
- Selected state from `selector.selectedX() === cell.x && selector.selectedY() === cell.y`
- Direction highlight from selector computed
- Cell editor panel receives `selector.selectedCell()`

- [ ] **Step 3: Add @HostListener for keyboard navigation**

Add to grid-editor.component.ts (ported from old `GridComponent`):

```typescript
@HostListener('window:keydown', ['$event'])
handleKey(event: KeyboardEvent): void {
  const target = event.target as HTMLElement;
  const tag = target.tagName.toLowerCase();
  if (tag === 'input' || tag === 'textarea' || target.isContentEditable) return;
  if (event.ctrlKey || event.metaKey || event.altKey) return;
  if (['Control', 'Meta', 'Alt', 'AltGraph'].includes(event.key)) return;

  switch (event.key) {
    case 'ArrowRight': this.selector.moveRight(); break;
    case 'ArrowLeft': this.selector.moveLeft(); break;
    case 'ArrowDown': this.selector.moveDown(); break;
    case 'ArrowUp': this.selector.moveUp(); break;
    case 'Backspace': this.selector.handleBackspace(); event.preventDefault(); break;
    case 'Delete': this.selector.handleDelete(); event.preventDefault(); break;
    case 'Shift': this.selector.handleShift(); break;
    default:
      if (/^[a-zA-Z]$/.test(event.key)) {
        this.selector.inputLetter(event.key);
        event.preventDefault();
      }
  }
}
```

### Task 14: Refactor EditorCellComponent for domain Cell

**Files:**
- Modify: `presentation/features/grid-editor/editor/editor-cell.component.ts`
- Modify: `presentation/features/grid-editor/editor/editor-cell.component.scss`

- [ ] **Step 1: Update to use domain Cell + add direction highlight**

```typescript
import { Component, computed, inject, Input } from '@angular/core';
import { Cell, Direction, SeparatorType } from '@domain/models/grid.model';
import { isCellClue, isCellLetter } from '@domain/services/cell-utils';
import { GridSelectorService } from '@application/services/grid-selector.service';

@Component({
  selector: 'app-editor-cell',
  standalone: true,
  template: `
    <div class="ec"
      [class.ec--letter]="cell.type === 'LETTER'"
      [class.ec--black]="cell.type === 'BLACK'"
      [class.ec--clue-single]="cell.type === 'CLUE_SINGLE'"
      [class.ec--clue-double]="cell.type === 'CLUE_DOUBLE'"
      [class.ec--selected]="isSelected()"
      [class.ec--on-direction]="isOnDirection()"
      [class.ec--separator-left]="hasLeftSeparator()"
      [class.ec--separator-up]="hasUpSeparator()"
      (click)="onClick()"
    >
      @switch (cell.type) {
        @case ('LETTER') {
          <span class="ec__letter">{{ cell.letter?.value }}</span>
          @if (cell.letter?.number) {
            <span class="ec__number">{{ cell.letter.number }}</span>
          }
        }
        @case ('BLACK') { }
        @case ('CLUE_SINGLE') {
          <span class="ec__clue-icon">→</span>
          <span class="ec__clue-text">{{ cell.clues?.[0]?.text?.slice(0, 12) }}</span>
        }
        @case ('CLUE_DOUBLE') {
          <span class="ec__clue-icon">↗</span>
          <span class="ec__clue-text">{{ cell.clues?.[0]?.text?.slice(0, 8) }}</span>
        }
      }
    </div>
  `,
  styleUrl: './editor-cell.component.scss',
})
export class EditorCellComponent {
  @Input() cell!: Cell;

  private selector = inject(GridSelectorService);

  isSelected = computed(() =>
    this.selector.selectedX() === this.cell.x && this.selector.selectedY() === this.cell.y
  );

  isOnDirection = computed(() => {
    const dir = this.selector.direction();
    if (dir === 'NONE' || !isCellLetter(this.cell)) return false;
    const sx = this.selector.selectedX();
    const sy = this.selector.selectedY();
    if (dir === 'DOWNWARDS') return this.cell.x === sx && this.cell.y > sy;
    if (dir === 'RIGHTWARDS') return this.cell.y === sy && this.cell.x > sx;
    return false;
  });

  onClick(): void {
    this.selector.selectOnClick(this.cell.x, this.cell.y);
  }

  hasLeftSeparator(): boolean {
    return this.cell.letter?.separator === 'LEFT' || this.cell.letter?.separator === 'BOTH';
  }

  hasUpSeparator(): boolean {
    return this.cell.letter?.separator === 'UP' || this.cell.letter?.separator === 'BOTH';
  }
}
```

- [ ] **Step 2: Update SCSS with direction highlight + separator styles**

Add to editor-cell.component.scss:

```scss
// Direction highlight (cells ahead in current direction)
&--on-direction {
  z-index: 1;
  outline: 1px solid var(--color-forest-light);
  outline-offset: -1px;
  background-color: rgba(122, 184, 154, 0.08) !important;
}

// Separator borders (thick lines between word groups)
&--separator-up {
  border-top: 2.5px solid var(--color-ink) !important;
}
&--separator-left {
  border-left: 2.5px solid var(--color-ink) !important;
}
```

### Task 15: Refactor CellEditorPanelComponent for domain Cell

**Files:**
- Modify: `presentation/features/grid-editor/editor/cell-editor-panel.component.ts`
- Modify: `presentation/features/grid-editor/editor/cell-editor-panel.component.html`

- [ ] **Step 1: Update to use domain Cell**

Key changes:
- Input type changes from `EditorCell | null` to `Cell | null`
- Emit full updated `Cell` instead of `Partial<EditorCell>`
- Form applies changes through domain cell-utils functions
- Inject `GridSelectorService` to push updates directly

- [ ] **Step 2: Auto-apply on change (no explicit Apply button needed)**

Wire form valueChanges to emit updates immediately on blur or change, matching old editor behavior.

### Task 16: Remove tools palette (merge into selector behavior)

The old editor didn't have a tool palette — it used cell type toggles in the sidebar. The current editor has both tools AND cell type toggles. Simplify:

- [ ] **Step 1: Keep SELECT as default mode**

When SELECT is active (default):
- Click on cell → select it (via selector)
- Keyboard nav works (arrows, letters, backspace, shift)
- Cell type change happens through the cell editor panel

- [ ] **Step 2: Tool buttons set cell type directly**

When a cell-type tool (LETTER, BLACK, CLUE_SINGLE, CLUE_DOUBLE) is active:
- Click on cell → changes its type AND selects it
- This calls `selector.onCellTypeChange()` then `selector.selectOnClick()`

---

## Chunk 6: Build, Verify, Docs, Commit

### Task 17: Build verification

- [ ] **Step 1: Full build**

Run: `cd cocro-web && npx ng build 2>&1 | tail -20`
Expected: 0 errors, 0 warnings (or only acceptable warnings)

- [ ] **Step 2: Verify runtime**

Run: `cd cocro-web && npx ng serve` and verify in browser:
- Grid editor loads at `/grids/editor`
- Click on cells selects them
- Arrow keys navigate
- Typing letters fills cells and auto-advances
- Shift toggles direction
- Backspace erases and moves back
- Cell type changes work (SELECT tool + panel toggles)
- Width/height resize works
- Save button works
- Coral line doesn't overlap sidebar

### Task 18: Update docs

- [ ] **Step 1: Update CLAUDE.md**

Add the Angular DDD structure to the Architecture section:

```markdown
### Angular Frontend (cocro-web)
DDD 4-folder structure:
- **domain/**: models, rules, services (pure functions, no Angular deps)
- **application/**: services (GridSelectorService), ports
- **infrastructure/**: adapters (HTTP services), auth, guards, DTOs
- **presentation/**: features (pages), shared components
```

### Task 19: Commit

- [ ] **Step 1: Stage and commit**

```bash
git add cocro-web/
git commit -m "feat(angular): restructure to DDD architecture + port grid editor behavior

- Restructure src/app into domain/application/infrastructure/presentation
- Port domain models, cell-utils, grid-utils, validation rules from old frontend
- Add signal-based GridSelectorService with keyboard navigation
- Refactor grid editor to use domain models and selector service
- Add direction highlight, separator borders, smart direction detection
- Fix coral line z-index overlap
- Add tsconfig path aliases (@domain, @application, @infrastructure, @presentation)"
```
