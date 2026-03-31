# Angular Architecture Refactor — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix 6 architecture violations in cocro-web where presentation components bypass the application layer (direct port calls, DTO building, domain computation, constructor side-effects). Enforce the DDD rule: components only talk to use cases and application services, never to ports or domain logic directly.

**Architecture:** cocro-web DDD 4-layer architecture: `domain/` (pure models + functions), `application/` (use cases, ports, services), `infrastructure/` (HTTP adapters, auth), `presentation/` (components only). Components inject use cases and application services. Use cases inject ports via `InjectionToken`. Domain functions are pure exports with no Angular dependencies.

**Tech Stack:** Angular 20, TypeScript 5.8, Signals, standalone components, Jasmine + TestBed, RxJS 7.8

---

## File Map

| File | Change |
|------|--------|
| `cocro-web/src/app/application/use-cases/load-grid.use-case.ts` | **Create** — wraps `gridPort.getGrid()` |
| `cocro-web/src/app/application/use-cases/load-grid.use-case.spec.ts` | **Create** — unit test |
| `cocro-web/src/app/application/use-cases/save-grid.use-case.ts` | **Create** — replaces `CreateGridUseCase`, handles create + update with DTO building |
| `cocro-web/src/app/application/use-cases/save-grid.use-case.spec.ts` | **Create** — unit test |
| `cocro-web/src/app/domain/services/grid-utils.service.ts` | **Modify** — add `buildGlobalCluePreview()` pure function |
| `cocro-web/src/app/domain/services/grid-utils.service.spec.ts` | **Modify** — add tests for `buildGlobalCluePreview()` |
| `cocro-web/src/app/application/service/letter-author.service.ts` | **Create** — signal-based letter authorship state |
| `cocro-web/src/app/application/service/letter-author.service.spec.ts` | **Create** — unit test |
| `cocro-web/src/app/presentation/features/grid/editor/grid-editor/grid-editor.component.ts` | **Modify** — use `LoadGridUseCase` and `SaveGridUseCase` |
| `cocro-web/src/app/presentation/features/grid/editor/global-clue-preview/global-clue-preview.component.ts` | **Modify** — use domain function |
| `cocro-web/src/app/presentation/features/grid/play/grid-player.component.ts` | **Modify** — use session use cases + `LetterAuthorService` |
| `cocro-web/src/app/presentation/features/grid/my-grids/my-grids.component.ts` | **Modify** — move `loadGrids()` to `ngOnInit()` |
| `cocro-web/src/app/application/use-cases/create-grid.use-case.ts` | **Delete** (replaced by `SaveGridUseCase`) |
| `cocro-web/src/app/application/use-cases/create-grid.use-case.spec.ts` | **Delete** |

---

## Task 1: Extract `buildGlobalCluePreview` domain function

Fixes violation — domain computation in `global-clue-preview.component.ts`.

**Files:**
- Modify: `cocro-web/src/app/domain/services/grid-utils.service.ts`
- Modify: `cocro-web/src/app/domain/services/grid-utils.service.spec.ts`
- Modify: `cocro-web/src/app/presentation/features/grid/editor/global-clue-preview/global-clue-preview.component.ts`

- [ ] **Step 1: Add failing tests for `buildGlobalCluePreview` in `grid-utils.service.spec.ts`**

Add to `cocro-web/src/app/domain/services/grid-utils.service.spec.ts`:

```typescript
import { buildGlobalCluePreview, createEmptyGrid } from './grid-utils.service';
import { Grid } from '@domain/models/grid.model';

describe('buildGlobalCluePreview', () => {
  it('should return empty array when no globalClue', () => {
    const grid = createEmptyGrid('1', 'Test', 3, 3);
    expect(buildGlobalCluePreview(grid)).toEqual([]);
  });

  it('should return empty array when wordLengths is empty', () => {
    const grid: Grid = {
      ...createEmptyGrid('1', 'Test', 3, 3),
      globalClue: { label: 'Enigma', wordLengths: [] },
    };
    expect(buildGlobalCluePreview(grid)).toEqual([]);
  });

  it('should build preview words from numbered cells', () => {
    const grid = createEmptyGrid('1', 'Test', 3, 3);
    grid.cells[0] = { x: 0, y: 0, type: 'LETTER', letter: { value: 'A', separator: 'NONE', number: 1 } };
    grid.cells[1] = { x: 1, y: 0, type: 'LETTER', letter: { value: 'B', separator: 'NONE', number: 2 } };
    grid.cells[2] = { x: 2, y: 0, type: 'LETTER', letter: { value: 'C', separator: 'NONE', number: 3 } };

    const result = buildGlobalCluePreview({
      ...grid,
      globalClue: { label: 'Test', wordLengths: [2, 1] },
    });

    expect(result).toEqual([
      [{ letter: 'A', index: 1 }, { letter: 'B', index: 2 }],
      [{ letter: 'C', index: 3 }],
    ]);
  });

  it('should return empty string for missing numbered cells', () => {
    const grid = createEmptyGrid('1', 'Test', 3, 3);
    grid.cells[0] = { x: 0, y: 0, type: 'LETTER', letter: { value: 'X', separator: 'NONE', number: 1 } };

    const result = buildGlobalCluePreview({
      ...grid,
      globalClue: { label: 'Test', wordLengths: [2] },
    });

    expect(result).toEqual([
      [{ letter: 'X', index: 1 }, { letter: '', index: 2 }],
    ]);
  });
});
```

- [ ] **Step 2: Run the tests — confirm they fail**

```bash
cd cocro-web && npx ng test --include="**/grid-utils.service.spec.ts" --watch=false --no-progress 2>&1 | tail -10
```

Expected: `buildGlobalCluePreview` not found error.

- [ ] **Step 3: Implement `buildGlobalCluePreview` in `grid-utils.service.ts`**

Add at the end of `cocro-web/src/app/domain/services/grid-utils.service.ts`:

```typescript
export interface GlobalCluePreviewCell {
  letter: string;
  index: number;
}

export function buildGlobalCluePreview(grid: Grid): GlobalCluePreviewCell[][] {
  const wordLengths = grid.globalClue?.wordLengths;
  if (!wordLengths?.length) return [];

  const letterByNumber = new Map<number, string>();
  for (const cell of grid.cells) {
    if (cell.letter?.number != null && cell.letter.value) {
      letterByNumber.set(cell.letter.number, cell.letter.value);
    }
  }

  const words: GlobalCluePreviewCell[][] = [];
  let offset = 0;
  for (const length of wordLengths) {
    const word: GlobalCluePreviewCell[] = [];
    for (let i = 1; i <= length; i++) {
      word.push({ letter: letterByNumber.get(offset + i) ?? '', index: offset + i });
    }
    offset += length;
    words.push(word);
  }
  return words;
}
```

- [ ] **Step 4: Run tests — confirm they pass**

```bash
cd cocro-web && npx ng test --include="**/grid-utils.service.spec.ts" --watch=false --no-progress 2>&1 | tail -5
```

Expected: `4 specs, 0 failures`

- [ ] **Step 5: Refactor `GlobalCluePreviewComponent` to use the domain function**

Replace content of `cocro-web/src/app/presentation/features/grid/editor/global-clue-preview/global-clue-preview.component.ts` with:

```typescript
import { Component, computed, inject } from '@angular/core';
import { GridSelectorService } from '@application/service/grid-selector.service';
import { buildGlobalCluePreview } from '@domain/services/grid-utils.service';

@Component({
  selector: 'cocro-global-clue-preview',
  standalone: true,
  templateUrl: './global-clue-preview.component.html',
  styleUrls: ['./global-clue-preview.component.scss'],
})
export class GlobalCluePreviewComponent {
  private readonly selectorService = inject(GridSelectorService);

  readonly previewWords = computed(() => buildGlobalCluePreview(this.selectorService.grid()));
}
```

- [ ] **Step 6: Run all tests**

```bash
cd cocro-web && npx ng test --watch=false --no-progress 2>&1 | tail -5
```

Expected: 0 failures.

- [ ] **Step 7: Commit**

```bash
git add cocro-web/src/app/domain/services/grid-utils.service.ts \
        cocro-web/src/app/domain/services/grid-utils.service.spec.ts \
        cocro-web/src/app/presentation/features/grid/editor/global-clue-preview/global-clue-preview.component.ts
git commit -m "refactor(angular): extract buildGlobalCluePreview to domain pure function

Move domain computation from GlobalCluePreviewComponent into a pure
function in grid-utils.service.ts. Component computed becomes a one-liner."
```

---

## Task 2: Create `LoadGridUseCase`

Fixes violation — direct `gridPort.getGrid()` call in `grid-editor.component.ts` constructor.

**Files:**
- Create: `cocro-web/src/app/application/use-cases/load-grid.use-case.spec.ts`
- Create: `cocro-web/src/app/application/use-cases/load-grid.use-case.ts`

- [ ] **Step 1: Write failing test**

Create `cocro-web/src/app/application/use-cases/load-grid.use-case.spec.ts`:

```typescript
import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { LoadGridUseCase } from './load-grid.use-case';
import { GridPort, GRID_PORT } from '@application/ports/grid/grid.port';
import { Grid } from '@domain/models/grid.model';

describe('LoadGridUseCase', () => {
  let useCase: LoadGridUseCase;
  let mockGridPort: jasmine.SpyObj<GridPort>;

  const GRID_STUB: Grid = {
    id: 'grid-1',
    title: 'Test Grid',
    width: 5,
    height: 5,
    cells: [],
  };

  beforeEach(() => {
    mockGridPort = jasmine.createSpyObj<GridPort>('GridPort', [
      'getGrid', 'getMyGrids', 'submitGrid', 'patchGrid',
    ]);

    TestBed.configureTestingModule({
      providers: [
        LoadGridUseCase,
        { provide: GRID_PORT, useValue: mockGridPort },
      ],
    });

    useCase = TestBed.inject(LoadGridUseCase);
  });

  it('should be created', () => {
    expect(useCase).toBeTruthy();
  });

  it('should delegate to gridPort.getGrid and return the grid', (done) => {
    mockGridPort.getGrid.and.returnValue(of(GRID_STUB));

    useCase.execute('grid-1').subscribe((grid) => {
      expect(grid).toEqual(GRID_STUB);
      expect(mockGridPort.getGrid).toHaveBeenCalledWith('grid-1');
      done();
    });
  });

  it('should propagate errors from gridPort.getGrid', (done) => {
    mockGridPort.getGrid.and.returnValue(throwError(() => new Error('Not found')));

    useCase.execute('bad-id').subscribe({
      error: (err) => {
        expect(err.message).toBe('Not found');
        done();
      },
    });
  });
});
```

- [ ] **Step 2: Run test — confirm failure**

```bash
cd cocro-web && npx ng test --include="**/load-grid.use-case.spec.ts" --watch=false --no-progress 2>&1 | tail -5
```

Expected: module not found error.

- [ ] **Step 3: Implement `LoadGridUseCase`**

Create `cocro-web/src/app/application/use-cases/load-grid.use-case.ts`:

```typescript
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { GRID_PORT } from '@application/ports/grid/grid.port';
import { Grid } from '@domain/models/grid.model';

@Injectable({ providedIn: 'root' })
export class LoadGridUseCase {
  private readonly gridPort = inject(GRID_PORT);

  execute(gridId: string): Observable<Grid> {
    return this.gridPort.getGrid(gridId);
  }
}
```

- [ ] **Step 4: Run test — confirm pass**

```bash
cd cocro-web && npx ng test --include="**/load-grid.use-case.spec.ts" --watch=false --no-progress 2>&1 | tail -5
```

Expected: `3 specs, 0 failures`

- [ ] **Step 5: Commit**

```bash
git add cocro-web/src/app/application/use-cases/load-grid.use-case.ts \
        cocro-web/src/app/application/use-cases/load-grid.use-case.spec.ts
git commit -m "feat(angular): add LoadGridUseCase

Wraps gridPort.getGrid() behind a use case so presentation components
do not call ports directly."
```

---

## Task 3: Create `SaveGridUseCase` (replaces `CreateGridUseCase`)

Fixes violation — DTO building + conditional create/patch logic in `grid-editor.component.ts`.

**Files:**
- Create: `cocro-web/src/app/application/use-cases/save-grid.use-case.spec.ts`
- Create: `cocro-web/src/app/application/use-cases/save-grid.use-case.ts`
- Delete: `cocro-web/src/app/application/use-cases/create-grid.use-case.ts`
- Delete: `cocro-web/src/app/application/use-cases/create-grid.use-case.spec.ts`

- [ ] **Step 1: Write failing test**

Create `cocro-web/src/app/application/use-cases/save-grid.use-case.spec.ts`:

```typescript
import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { SaveGridUseCase } from './save-grid.use-case';
import { GridPort, GRID_PORT } from '@application/ports/grid/grid.port';
import { EditorDraftPort, EDITOR_DRAFT_PORT } from '@application/ports/editor/editor-draft.port';
import { Grid } from '@domain/models/grid.model';

describe('SaveGridUseCase', () => {
  let useCase: SaveGridUseCase;
  let mockGridPort: jasmine.SpyObj<GridPort>;
  let mockEditorDraft: jasmine.SpyObj<EditorDraftPort>;

  const GRID_STUB: Grid = {
    id: 'grid-1',
    title: 'Ma grille',
    reference: 'Ref-1',
    difficulty: '2',
    description: 'Description',
    width: 5,
    height: 5,
    cells: [
      { x: 0, y: 0, type: 'LETTER', letter: { value: 'A', separator: 'NONE' } },
    ],
    globalClue: { label: 'Enigma', wordLengths: [3, 2] },
  };

  beforeEach(() => {
    mockGridPort = jasmine.createSpyObj<GridPort>('GridPort', [
      'getGrid', 'getMyGrids', 'submitGrid', 'patchGrid',
    ]);
    mockEditorDraft = jasmine.createSpyObj<EditorDraftPort>('EditorDraftPort', [
      'save', 'load', 'clear',
    ]);

    TestBed.configureTestingModule({
      providers: [
        SaveGridUseCase,
        { provide: GRID_PORT, useValue: mockGridPort },
        { provide: EDITOR_DRAFT_PORT, useValue: mockEditorDraft },
      ],
    });

    useCase = TestBed.inject(SaveGridUseCase);
  });

  it('should be created', () => {
    expect(useCase).toBeTruthy();
  });

  describe('create', () => {
    it('should build SubmitGridRequest from Grid and call submitGrid', async () => {
      mockGridPort.submitGrid.and.returnValue(of({ gridId: 'new-id' }));

      const result = await useCase.create(GRID_STUB);

      expect(result).toBe('new-id');
      expect(mockGridPort.submitGrid).toHaveBeenCalledWith(
        jasmine.objectContaining({
          title: 'Ma grille',
          reference: 'Ref-1',
          difficulty: '2',
          description: 'Description',
          width: 5,
          height: 5,
          globalClueLabel: 'Enigma',
          globalClueWordLengths: [3, 2],
        }),
      );
    });

    it('should clear the editor draft after successful create', async () => {
      mockGridPort.submitGrid.and.returnValue(of({ gridId: 'new-id' }));

      await useCase.create(GRID_STUB);

      expect(mockEditorDraft.clear).toHaveBeenCalledTimes(1);
    });

    it('should map cells through cellToDto', async () => {
      mockGridPort.submitGrid.and.returnValue(of({ gridId: 'new-id' }));

      await useCase.create(GRID_STUB);

      const call = mockGridPort.submitGrid.calls.mostRecent().args[0];
      expect(call.cells[0]).toEqual(
        jasmine.objectContaining({ x: 0, y: 0, type: 'LETTER', letter: 'A' }),
      );
    });

    it('should default difficulty to NONE when undefined', async () => {
      mockGridPort.submitGrid.and.returnValue(of({ gridId: 'new-id' }));
      const gridNoDifficulty: Grid = { ...GRID_STUB, difficulty: undefined };

      await useCase.create(gridNoDifficulty);

      const call = mockGridPort.submitGrid.calls.mostRecent().args[0];
      expect(call.difficulty).toBe('NONE');
    });
  });

  describe('update', () => {
    it('should build PatchGridRequest from Grid and call patchGrid', async () => {
      mockGridPort.patchGrid.and.returnValue(of(undefined));

      await useCase.update(GRID_STUB);

      expect(mockGridPort.patchGrid).toHaveBeenCalledWith(
        jasmine.objectContaining({
          gridId: 'grid-1',
          title: 'Ma grille',
          reference: 'Ref-1',
          difficulty: '2',
          width: 5,
          height: 5,
          globalClueLabel: 'Enigma',
          globalClueWordLengths: [3, 2],
        }),
      );
    });

    it('should not clear editor draft on update', async () => {
      mockGridPort.patchGrid.and.returnValue(of(undefined));

      await useCase.update(GRID_STUB);

      expect(mockEditorDraft.clear).not.toHaveBeenCalled();
    });
  });
});
```

- [ ] **Step 2: Run test — confirm failure**

```bash
cd cocro-web && npx ng test --include="**/save-grid.use-case.spec.ts" --watch=false --no-progress 2>&1 | tail -5
```

Expected: module not found error.

- [ ] **Step 3: Implement `SaveGridUseCase`**

Create `cocro-web/src/app/application/use-cases/save-grid.use-case.ts`:

```typescript
import { Inject, Injectable } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { GridPort, GRID_PORT } from '@application/ports/grid/grid.port';
import { EditorDraftPort, EDITOR_DRAFT_PORT } from '@application/ports/editor/editor-draft.port';
import { cellToDto, PatchGridRequest, SubmitGridRequest } from '@application/dto/grid.dto';
import { Grid } from '@domain/models/grid.model';

@Injectable({ providedIn: 'root' })
export class SaveGridUseCase {
  constructor(
    @Inject(GRID_PORT) private readonly gridPort: GridPort,
    @Inject(EDITOR_DRAFT_PORT) private readonly editorDraft: EditorDraftPort,
  ) {}

  async create(grid: Grid): Promise<string> {
    const request: SubmitGridRequest = {
      title: grid.title,
      reference: grid.reference,
      difficulty: grid.difficulty ?? 'NONE',
      description: grid.description,
      width: grid.width,
      height: grid.height,
      cells: grid.cells.map(cellToDto),
      globalClueLabel: grid.globalClue?.label,
      globalClueWordLengths: grid.globalClue?.wordLengths,
    };
    const response = await firstValueFrom(this.gridPort.submitGrid(request));
    this.editorDraft.clear();
    return response.gridId;
  }

  async update(grid: Grid): Promise<void> {
    const request: PatchGridRequest = {
      gridId: grid.id,
      title: grid.title,
      reference: grid.reference,
      difficulty: grid.difficulty ?? 'NONE',
      description: grid.description,
      width: grid.width,
      height: grid.height,
      cells: grid.cells.map(cellToDto),
      globalClueLabel: grid.globalClue?.label,
      globalClueWordLengths: grid.globalClue?.wordLengths,
    };
    await firstValueFrom(this.gridPort.patchGrid(request));
  }
}
```

- [ ] **Step 4: Run test — confirm pass**

```bash
cd cocro-web && npx ng test --include="**/save-grid.use-case.spec.ts" --watch=false --no-progress 2>&1 | tail -5
```

Expected: `6 specs, 0 failures`

- [ ] **Step 5: Delete old use cases**

```bash
git rm cocro-web/src/app/application/use-cases/create-grid.use-case.ts \
       cocro-web/src/app/application/use-cases/create-grid.use-case.spec.ts
```

- [ ] **Step 6: Commit**

```bash
git add cocro-web/src/app/application/use-cases/save-grid.use-case.ts \
        cocro-web/src/app/application/use-cases/save-grid.use-case.spec.ts
git commit -m "refactor(angular): replace CreateGridUseCase with SaveGridUseCase

SaveGridUseCase takes a Grid domain model and handles DTO building
internally. Provides create() and update() methods. Removes the
now-redundant CreateGridUseCase."
```

---

## Task 4: Create `LetterAuthorService`

Fixes violation — `letterAuthors` signal state managed inside `grid-player.component.ts`.

**Files:**
- Create: `cocro-web/src/app/application/service/letter-author.service.spec.ts`
- Create: `cocro-web/src/app/application/service/letter-author.service.ts`

- [ ] **Step 1: Write failing test**

Create `cocro-web/src/app/application/service/letter-author.service.spec.ts`:

```typescript
import { TestBed } from '@angular/core/testing';
import { LetterAuthorService } from './letter-author.service';

describe('LetterAuthorService', () => {
  let service: LetterAuthorService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [LetterAuthorService],
    });
    service = TestBed.inject(LetterAuthorService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should return undefined for unknown coordinates', () => {
    expect(service.getAuthor(0, 0)).toBeUndefined();
  });

  it('should set and get an author', () => {
    service.setAuthor(3, 5, 'user-42');
    expect(service.getAuthor(3, 5)).toBe('user-42');
  });

  it('should overwrite an existing author', () => {
    service.setAuthor(1, 2, 'alice');
    service.setAuthor(1, 2, 'bob');
    expect(service.getAuthor(1, 2)).toBe('bob');
  });

  it('should clear a single author', () => {
    service.setAuthor(4, 7, 'user-1');
    service.clearAuthor(4, 7);
    expect(service.getAuthor(4, 7)).toBeUndefined();
  });

  it('should clear all authors', () => {
    service.setAuthor(0, 0, 'a');
    service.setAuthor(1, 1, 'b');
    service.clearAll();
    expect(service.getAuthor(0, 0)).toBeUndefined();
    expect(service.getAuthor(1, 1)).toBeUndefined();
  });

  it('should expose authors as a readable signal', () => {
    service.setAuthor(2, 3, 'me');
    const authors = service.authors();
    expect(authors.get('2,3')).toBe('me');
  });
});
```

- [ ] **Step 2: Run test — confirm failure**

```bash
cd cocro-web && npx ng test --include="**/letter-author.service.spec.ts" --watch=false --no-progress 2>&1 | tail -5
```

Expected: module not found error.

- [ ] **Step 3: Implement `LetterAuthorService`**

Create `cocro-web/src/app/application/service/letter-author.service.ts`:

```typescript
import { Injectable, signal } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class LetterAuthorService {
  private readonly _authors = signal(new Map<string, string>());

  readonly authors = this._authors.asReadonly();

  getAuthor(x: number, y: number): string | undefined {
    return this._authors().get(`${x},${y}`);
  }

  setAuthor(x: number, y: number, authorId: string): void {
    this._authors.update(m => {
      const next = new Map(m);
      next.set(`${x},${y}`, authorId);
      return next;
    });
  }

  clearAuthor(x: number, y: number): void {
    this._authors.update(m => {
      const next = new Map(m);
      next.delete(`${x},${y}`);
      return next;
    });
  }

  clearAll(): void {
    this._authors.set(new Map());
  }
}
```

- [ ] **Step 4: Run test — confirm pass**

```bash
cd cocro-web && npx ng test --include="**/letter-author.service.spec.ts" --watch=false --no-progress 2>&1 | tail -5
```

Expected: `7 specs, 0 failures`

- [ ] **Step 5: Commit**

```bash
git add cocro-web/src/app/application/service/letter-author.service.ts \
        cocro-web/src/app/application/service/letter-author.service.spec.ts
git commit -m "feat(angular): add LetterAuthorService for letter authorship tracking

Extracts letter author state from GridPlayerComponent into a dedicated
application service with signal-based reactivity."
```

---

## Task 5: Fix `my-grids.component.ts` — constructor side-effect

Fixes violation — `loadGrids()` called in constructor instead of `ngOnInit`.

**Files:**
- Modify: `cocro-web/src/app/presentation/features/grid/my-grids/my-grids.component.ts`

- [ ] **Step 1: Add `OnInit` to `MyGridsComponent`**

Replace the content of `cocro-web/src/app/presentation/features/grid/my-grids/my-grids.component.ts` with:

```typescript
import { Component, inject, OnInit, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { GetMyGridsUseCase } from '@application/use-cases/get-my-grids.use-case';
import { CreateSessionUseCase } from '@application/use-cases/create-session.use-case';
import { GridSummary } from '@domain/models/grid-summary.model';
import { ToastService } from '@presentation/shared/components/toast/toast.service';
import { GridCardComponent } from './grid-card/grid-card.component';
import { ButtonComponent } from '@presentation/shared/components/button/button.component';

@Component({
  selector: 'cocro-my-grids',
  standalone: true,
  imports: [GridCardComponent, ButtonComponent, RouterLink],
  templateUrl: './my-grids.component.html',
  styleUrls: ['./my-grids.component.scss'],
})
export class MyGridsComponent implements OnInit {
  private readonly getMyGrids = inject(GetMyGridsUseCase);
  private readonly createSession = inject(CreateSessionUseCase);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);

  readonly grids = signal<GridSummary[]>([]);
  readonly loading = signal(true);
  readonly error = signal('');
  readonly launching = signal<string | null>(null);

  ngOnInit(): void {
    this.loadGrids();
  }

  private loadGrids(): void {
    this.loading.set(true);
    this.error.set('');
    this.getMyGrids.execute().subscribe({
      next: (grids) => {
        this.grids.set(grids);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Impossible de charger vos grilles.');
        this.loading.set(false);
      },
    });
  }

  onLaunch(grid: GridSummary): void {
    if (this.launching()) return;
    this.launching.set(grid.gridId);
    this.createSession.execute(grid.gridId).subscribe({
      next: (session) => {
        this.router.navigate(['/play', session.shareCode]);
      },
      error: () => {
        this.toast.error('Impossible de lancer la session.');
        this.launching.set(null);
      },
    });
  }

  onEdit(grid: GridSummary): void {
    this.router.navigate(['/grid', grid.gridId, 'edit']);
  }

  retry(): void {
    this.loadGrids();
  }
}
```

- [ ] **Step 2: Run tests**

```bash
cd cocro-web && npx ng test --include="**/my-grids.component.spec.ts" --watch=false --no-progress 2>&1 | tail -5
```

Expected: all specs pass. (`fixture.detectChanges()` triggers `ngOnInit` in tests, so the flow is unchanged.)

- [ ] **Step 3: Commit**

```bash
git add cocro-web/src/app/presentation/features/grid/my-grids/my-grids.component.ts
git commit -m "refactor(angular): move MyGridsComponent.loadGrids() to ngOnInit

Remove constructor side-effect. Data loading now happens in ngOnInit
lifecycle hook as per Angular best practices."
```

---

## Task 6: Refactor `GridEditorComponent` — use `LoadGridUseCase` + `SaveGridUseCase`

**Files:**
- Modify: `cocro-web/src/app/presentation/features/grid/editor/grid-editor/grid-editor.component.ts`

- [ ] **Step 1: Replace the component**

Replace the entire content of `cocro-web/src/app/presentation/features/grid/editor/grid-editor/grid-editor.component.ts` with:

```typescript
import { Component, computed, effect, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { createEmptyGrid } from '@domain/services/grid-utils.service';
import { EDITOR_DRAFT_PORT } from '@application/ports/editor/editor-draft.port';
import { LoadGridUseCase } from '@application/use-cases/load-grid.use-case';
import { SaveGridUseCase } from '@application/use-cases/save-grid.use-case';

import { GridComponent } from '@presentation/shared/grid/grid-wrapper/grid.component';
import { GridSelectorService } from '@application/service/grid-selector.service';
import { CardComponent } from '@presentation/shared/components/card/card.component';
import { ButtonComponent } from '@presentation/shared/components/button/button.component';
import { ToastService } from '@presentation/shared/components/toast/toast.service';
import { ClueEditorComponent } from '@presentation/features/grid/editor/clue-editor/clue-editor.component';
import { LetterEditorComponent } from '@presentation/features/grid/editor/letter-editor/letter-editor.component';
import { GridParamsComponent } from '@presentation/features/grid/editor/grid-params/grid-params.component';
import { CellTypeComponent } from '@presentation/features/grid/editor/cell-type/cell-type.component';
import { GlobalClueEditorComponent } from '@presentation/features/grid/editor/global-clue-editor/global-clue-editor.component';
import { GlobalCluePreviewComponent } from '@presentation/features/grid/editor/global-clue-preview/global-clue-preview.component';

@Component({
  selector: 'cocro-grid-editor',
  standalone: true,
  imports: [
    GridComponent,
    CardComponent,
    ButtonComponent,
    ClueEditorComponent,
    LetterEditorComponent,
    GridParamsComponent,
    CellTypeComponent,
    GlobalClueEditorComponent,
    GlobalCluePreviewComponent,
  ],
  templateUrl: './grid-editor.component.html',
  styleUrls: ['./grid-editor.component.scss'],
})
export class GridEditorComponent {
  private readonly loadGridUseCase = inject(LoadGridUseCase);
  private readonly saveGridUseCase = inject(SaveGridUseCase);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  readonly selectorService = inject(GridSelectorService);
  private readonly toast = inject(ToastService);
  private readonly draft = inject(EDITOR_DRAFT_PORT);

  readonly saving = signal(false);
  readonly showGlobalClue = signal(false);
  readonly isEditMode = signal(false);

  readonly submitLabel = computed(() => this.isEditMode() ? 'Enregistrer' : 'Créer la grille');

  toggleGlobalClue(): void {
    if (this.showGlobalClue()) {
      this.selectorService.clearEnigmaData();
    }
    this.showGlobalClue.update(v => !v);
  }

  readonly isClueSelected = computed(() => {
    const type = this.selectorService.selectedCell()?.type;
    return type === 'CLUE_SINGLE' || type === 'CLUE_DOUBLE';
  });

  readonly isLetterSelected = computed(() => {
    return this.selectorService.selectedCell()?.type === 'LETTER';
  });

  constructor() {
    const gridId = this.route.snapshot.paramMap.get('gridId');

    if (gridId) {
      this.isEditMode.set(true);
      this.selectorService.initGrid(createEmptyGrid('0', 'Chargement...', 10, 13));
      firstValueFrom(this.loadGridUseCase.execute(gridId)).then((grid) => {
        this.selectorService.initGrid(grid);
      }).catch(() => {
        this.toast.error('Grille introuvable.');
        this.router.navigate(['/grid/mine']);
      });
    } else {
      const draft = this.draft.load();
      this.selectorService.initGrid(draft ?? createEmptyGrid('0', 'Nouvelle grille', 10, 13));
      effect(() => {
        this.draft.save(this.selectorService.grid());
      });
    }
  }

  resetDraft(): void {
    this.draft.clear();
    this.selectorService.initGrid(createEmptyGrid('0', 'Nouvelle grille', 10, 13));
    this.showGlobalClue.set(false);
  }

  async onSubmit() {
    this.saving.set(true);
    try {
      const grid = this.selectorService.grid();

      if (this.isEditMode()) {
        await this.saveGridUseCase.update(grid);
        this.toast.success('Grille mise à jour.');
        await this.router.navigate(['/grid/mine']);
      } else {
        await this.saveGridUseCase.create(grid);
        this.toast.success('Grille créée avec succès !');
        this.router.navigate(['/grid/mine']);
      }
    } catch (e: any) {
      const message = e.message ? e.message : 'Erreur inconnue';
      this.toast.error(message);
    } finally {
      this.saving.set(false);
    }
  }
}
```

- [ ] **Step 2: Verify no remaining imports of deleted use cases**

```bash
grep -r "CreateGridUseCase\|cellToDto\|PatchGridRequest\|SubmitGridRequest\|GRID_PORT" \
  cocro-web/src/app/presentation/features/grid/editor/grid-editor/grid-editor.component.ts \
  || echo "Clean"
```

Expected: `Clean`

- [ ] **Step 3: Run all tests**

```bash
cd cocro-web && npx ng test --watch=false --no-progress 2>&1 | tail -5
```

Expected: 0 failures.

- [ ] **Step 4: Commit**

```bash
git add cocro-web/src/app/presentation/features/grid/editor/grid-editor/grid-editor.component.ts
git commit -m "refactor(angular): use LoadGridUseCase + SaveGridUseCase in GridEditorComponent

Remove direct port calls and DTO building from the component.
LoadGridUseCase wraps gridPort.getGrid(). SaveGridUseCase handles
DTO construction and create/update logic internally."
```

---

## Task 7: Refactor `GridPlayerComponent` — use session use cases + `LetterAuthorService`

**Files:**
- Modify: `cocro-web/src/app/presentation/features/grid/play/grid-player.component.ts`

- [ ] **Step 1: Replace the component**

Replace the entire content of `cocro-web/src/app/presentation/features/grid/play/grid-player.component.ts` with:

```typescript
import { Component, computed, HostListener, inject, OnDestroy, OnInit, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { AuthService } from '@infrastructure/auth/auth.service';
import { SESSION_SOCKET_PORT } from '@application/ports/session/session-socket.port';
import { GAME_SESSION_PORT } from '@application/ports/session/game-session.port';
import { JoinSessionUseCase } from '@application/use-cases/join-session.use-case';
import { SyncSessionUseCase } from '@application/use-cases/sync-session.use-case';
import { LeaveSessionUseCase } from '@application/use-cases/leave-session.use-case';
import { LetterAuthorService } from '@application/service/letter-author.service';
import { GridSelectorService } from '@application/service/grid-selector.service';
import { createEmptyGrid } from '@domain/services/grid-utils.service';
import { mapGridTemplateToGrid } from '@infrastructure/adapters/session/grid-template.mapper';
import { getNetworkErrorMessage } from '@infrastructure/http/network-error';
import {
  GridCheckedEvent,
  GridUpdatedEvent,
  ParticipantJoinedEvent,
  ParticipantLeftEvent,
  SessionEvent,
  SessionWelcomeEvent,
  SyncRequiredEvent,
} from '@domain/models/session-events.model';
import { CellStateDto, GridCheckResponse, SessionFullResponse, SessionStatus } from '@domain/models/session.model';
import { CardComponent } from '@presentation/shared/components/card/card.component';
import { GridComponent } from '@presentation/shared/grid/grid-wrapper/grid.component';
import { GlobalCluePreviewComponent } from '@presentation/features/grid/editor/global-clue-preview/global-clue-preview.component';
import { PlayHeaderComponent } from './play-header/play-header.component';
import { PlayInfoComponent } from './play-info/play-info.component';

@Component({
  selector: 'cocro-grid-player',
  standalone: true,
  imports: [
    CardComponent,
    GridComponent,
    GlobalCluePreviewComponent,
    PlayHeaderComponent,
    PlayInfoComponent,
  ],
  templateUrl: './grid-player.component.html',
  styleUrl: './grid-player.component.scss',
})
export class GridPlayerComponent implements OnInit, OnDestroy {
  readonly shareCode = signal('');
  readonly status = signal<SessionStatus>('PLAYING');
  readonly participantCount = signal(0);
  readonly revision = signal(0);
  readonly connected = signal(false);
  readonly loading = signal(true);
  readonly gridLoaded = signal(false);
  readonly error = signal<string | null>(null);
  readonly checkResult = signal<GridCheckedEvent | null>(null);

  private readonly letterAuthors = inject(LetterAuthorService);

  readonly getCellColorClass = (x: number, y: number): string => {
    const author = this.letterAuthors.getAuthor(x, y);
    if (!author) return '';
    return author === 'me' ? 'letter--mine' : 'letter--other';
  };

  private readonly route = inject(ActivatedRoute);
  private readonly auth = inject(AuthService);
  private readonly sessionSocket = inject(SESSION_SOCKET_PORT);
  private readonly joinSession = inject(JoinSessionUseCase);
  private readonly syncSession = inject(SyncSessionUseCase);
  private readonly leaveSession = inject(LeaveSessionUseCase);
  private readonly gameSession = inject(GAME_SESSION_PORT);
  readonly selector = inject(GridSelectorService);
  private readonly router = inject(Router);

  readonly myUserId = computed(() => this.auth.currentUser()?.userId ?? '');

  readonly hasGlobalClue = computed(() => !!this.selector.grid().globalClue?.label);

  ngOnInit(): void {
    const shareCode = this.route.snapshot.paramMap.get('shareCode') ?? '';
    this.shareCode.set(shareCode);

    const token = this.auth.token();
    if (!token) {
      this.router.navigate(['/auth/login']);
      return;
    }

    this.joinSession.execute(shareCode).subscribe({
      next: (fullDto: SessionFullResponse) => {
        this.selector.initGrid(mapGridTemplateToGrid(fullDto.gridTemplate));
        fullDto.cells.forEach((c: CellStateDto) => {
          if (c.letter) this.selector.setLetterAt(c.x, c.y, c.letter);
        });
        this.revision.set(fullDto.gridRevision);
        this.participantCount.set(fullDto.participantCount);
        this.status.set(fullDto.status);
        this.gridLoaded.set(true);
        this.loading.set(false);

        this.sessionSocket.connect(token, shareCode, (event) => {
          this.connected.set(true);
          this.handleEvent(event);
        });
      },
      error: (err: unknown) => {
        this.error.set(getNetworkErrorMessage(err, 'Impossible de rejoindre la session.'));
        this.loading.set(false);
      },
    });
  }

  ngOnDestroy(): void {
    this.sessionSocket.disconnect();
    this.selector.initGrid(createEmptyGrid('0', '', 10, 10));
    this.letterAuthors.clearAll();
  }

  @HostListener('window:keydown', ['$event'])
  onKeyDown(event: KeyboardEvent): void {
    if (!this.gridLoaded() || this.loading()) return;

    const target = event.target as HTMLElement;
    const tag = target.tagName.toLowerCase();
    if (tag === 'input' || tag === 'textarea' || target.isContentEditable) return;

    const hasModifier =
      event.ctrlKey ||
      event.metaKey ||
      event.altKey ||
      event.getModifierState?.('AltGraph');
    if (
      hasModifier ||
      event.key === 'Control' ||
      event.key === 'Meta' ||
      event.key === 'Alt' ||
      event.key === 'AltGraph'
    ) return;

    const x = this.selector.selectedX();
    const y = this.selector.selectedY();

    switch (event.key) {
      case 'ArrowRight': this.selector.moveRight(); break;
      case 'ArrowLeft':  this.selector.moveLeft();  break;
      case 'ArrowDown':  this.selector.moveDown();  break;
      case 'ArrowUp':    this.selector.moveUp();    break;
      case 'Shift':
        this.selector.handleShift();
        break;
      case 'Backspace':
      case 'Delete':
        this.selector.clearLetterAt(x, y);
        this.sessionSocket.sendGridUpdate(this.shareCode(), { posX: x, posY: y, commandType: 'CLEAR_CELL' });
        this.letterAuthors.clearAuthor(x, y);
        event.preventDefault();
        break;
      default:
        if (/^[a-zA-Z]$/.test(event.key)) {
          const letter = event.key.toUpperCase();
          this.selector.inputLetter(letter);
          this.sessionSocket.sendGridUpdate(this.shareCode(), { posX: x, posY: y, commandType: 'PLACE_LETTER', letter });
          this.letterAuthors.setAuthor(x, y, 'me');
          event.preventDefault();
        }
    }
  }

  leave(): void {
    this.leaveSession.execute(this.shareCode()).subscribe();
    this.sessionSocket.disconnect();
    this.router.navigate(['/']);
  }

  checkGrid(): void {
    this.gameSession.checkGrid(this.shareCode()).subscribe({
      next: (result: GridCheckResponse) => {
        this.checkResult.set({
          type: 'GridChecked',
          userId: this.myUserId(),
          isComplete: result.isComplete,
          correctCount: result.correctCount,
          totalCount: result.totalCount,
        } as GridCheckedEvent);
      },
    });
  }

  private handleEvent(event: SessionEvent): void {
    switch (event.type) {
      case 'SessionWelcome':
        this.onWelcome(event as SessionWelcomeEvent);
        break;
      case 'GridUpdated':
        this.onGridUpdated(event as GridUpdatedEvent);
        break;
      case 'ParticipantJoined':
        this.participantCount.set((event as ParticipantJoinedEvent).participantCount);
        break;
      case 'ParticipantLeft':
        this.participantCount.set((event as ParticipantLeftEvent).participantCount);
        break;
      case 'GridChecked':
        this.checkResult.set(event as GridCheckedEvent);
        break;
      case 'SessionEnded':
        this.status.set('ENDED');
        break;
      case 'SessionInterrupted':
        this.status.set('INTERRUPTED');
        break;
      case 'SyncRequired':
        this.resync((event as SyncRequiredEvent).currentRevision);
        break;
    }
  }

  private onWelcome(event: SessionWelcomeEvent): void {
    this.status.set(event.status);
    this.participantCount.set(event.participantCount);
    this.revision.set(event.gridRevision);
  }

  private onGridUpdated(event: GridUpdatedEvent): void {
    if (event.actorId === this.myUserId()) return;
    this.revision.set(this.revision() + 1);
    if (event.commandType === 'PLACE_LETTER' && event.letter) {
      this.selector.setLetterAt(event.posX, event.posY, event.letter);
      this.letterAuthors.setAuthor(event.posX, event.posY, event.actorId);
    } else if (event.commandType === 'CLEAR_CELL') {
      this.selector.clearLetterAt(event.posX, event.posY);
      this.letterAuthors.clearAuthor(event.posX, event.posY);
    }
  }

  private resync(_targetRevision: number): void {
    this.letterAuthors.clearAll();
    this.syncSession.execute(this.shareCode()).subscribe({
      next: (full: SessionFullResponse) => {
        this.revision.set(full.gridRevision);
        this.participantCount.set(full.participantCount);
        this.status.set(full.status);
        this.selector.clearAllLetters();
        full.cells.forEach((c: CellStateDto) => {
          if (c.letter) this.selector.setLetterAt(c.x, c.y, c.letter);
        });
      },
    });
  }
}
```

**Key changes:**
- `gameSession.joinSession()` → `joinSession.execute()`
- `gameSession.syncSession()` → `syncSession.execute()`
- `gameSession.leaveSession()` → `leaveSession.execute()`
- `private letterAuthors = signal(new Map())` → `inject(LetterAuthorService)`
- All `letterAuthors.update(m => ...)` → `letterAuthors.setAuthor/clearAuthor/clearAll()`
- `ngOnDestroy` adds `this.letterAuthors.clearAll()`
- `GAME_SESSION_PORT` remains only for `checkGrid()` (no use case exists for it yet)

- [ ] **Step 2: Verify no direct port calls for join/sync/leave remain**

```bash
grep -n "gameSession\.\(joinSession\|syncSession\|leaveSession\)" \
  cocro-web/src/app/presentation/features/grid/play/grid-player.component.ts \
  || echo "Clean"
```

Expected: `Clean`

- [ ] **Step 3: Run all tests**

```bash
cd cocro-web && npx ng test --watch=false --no-progress 2>&1 | tail -5
```

Expected: 0 failures.

- [ ] **Step 4: Commit**

```bash
git add cocro-web/src/app/presentation/features/grid/play/grid-player.component.ts
git commit -m "refactor(angular): use session use cases + LetterAuthorService in GridPlayerComponent

Replace direct GAME_SESSION_PORT calls (join/sync/leave) with use cases.
Extract letterAuthors signal into LetterAuthorService application service."
```

---

## Task 8: Final verification

- [ ] **Step 1: Run the full test suite**

```bash
cd cocro-web && npx ng test --watch=false --no-progress 2>&1 | tail -10
```

Expected: 0 failures.

- [ ] **Step 2: Run the build**

```bash
cd cocro-web && npx ng build 2>&1 | tail -5
```

Expected: 0 errors.

- [ ] **Step 3: Verify no port injections remain in presentation layer**

```bash
grep -rn "inject(GRID_PORT)" cocro-web/src/app/presentation/ || echo "Clean — no GRID_PORT in presentation"
```

Expected: `Clean — no GRID_PORT in presentation`

- [ ] **Step 4: Verify no DTO imports remain in presentation components**

```bash
grep -rn "cellToDto\|SubmitGridRequest\|PatchGridRequest" cocro-web/src/app/presentation/ || echo "Clean — no DTO imports in presentation"
```

Expected: `Clean — no DTO imports in presentation`
