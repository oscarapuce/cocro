# Mini-Grid Preview & Cell Number Position Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move the letter-cell number indicator from top-right to top-left, and add a mini-grid preview component above the main grid that mirrors the global clue answer structure live.

**Architecture:** Two independent changes. (1) A one-line CSS fix in `letter-input.component.scss`. (2) A new standalone `GlobalCluePreviewComponent` placed above `<cocro-grid />` in the grid editor, reading `GridSelectorService.grid()` reactively to compute a letter-by-number map and display Option-A layout (all words on one row, separated by a gap).

**Tech Stack:** Angular 20 standalone + Signals, SCSS with CSS custom properties.

---

## File Map

| File | Action | Purpose |
|------|--------|---------|
| `cocro-web/src/app/presentation/shared/grid/inputs/letter/letter-input.component.scss` | Modify | Move `.letter-input__number` from top-right to top-left |
| `cocro-web/src/app/presentation/features/grid/editor/global-clue-preview/global-clue-preview.component.ts` | Create | Reactive component computing word preview from grid signals |
| `cocro-web/src/app/presentation/features/grid/editor/global-clue-preview/global-clue-preview.component.html` | Create | Option-A single-row layout template |
| `cocro-web/src/app/presentation/features/grid/editor/global-clue-preview/global-clue-preview.component.scss` | Create | Mini-cell styles matching crossword aesthetic |
| `cocro-web/src/app/presentation/features/grid/editor/grid-editor/grid-editor.component.ts` | Modify | Import `GlobalCluePreviewComponent` |
| `cocro-web/src/app/presentation/features/grid/editor/grid-editor/grid-editor.component.html` | Modify | Add `<cocro-global-clue-preview>` above `<cocro-grid>` |

---

### Task 1: Move cell number indicator to top-left

**Files:**
- Modify: `cocro-web/src/app/presentation/shared/grid/inputs/letter/letter-input.component.scss:10-20`

- [ ] **Step 1: Apply the CSS change**

In `.letter-input__number`, remove `right: 2px`, replace with `left: 2px`, and change `top: 1px` to `top: 0px`:

```scss
.letter-input__number {
  position: absolute;
  top: 0px;
  left: 2px;
  font-size: 0.45rem;
  font-family: var(--font-ui);
  font-weight: 700;
  color: var(--color-ink-muted);
  line-height: 1;
  pointer-events: none;
}
```

- [ ] **Step 2: Verify build**

```bash
cd /Users/oscar_mallet/Documents/cocro/cocro-web && npx ng build 2>&1 | tail -5
```

Expected: `Build at: ... - Hash: ...` with 0 errors.

- [ ] **Step 3: Commit**

```bash
git add cocro-web/src/app/presentation/shared/grid/inputs/letter/letter-input.component.scss
git commit -m "fix(ui): move cell number indicator to top-left"
```

---

### Task 2: Create GlobalCluePreviewComponent

**Files:**
- Create: `cocro-web/src/app/presentation/features/grid/editor/global-clue-preview/global-clue-preview.component.ts`
- Create: `cocro-web/src/app/presentation/features/grid/editor/global-clue-preview/global-clue-preview.component.html`
- Create: `cocro-web/src/app/presentation/features/grid/editor/global-clue-preview/global-clue-preview.component.scss`

**Context:**
- `GridSelectorService.grid()` is a signal returning `Grid` — reactive, no subscription needed.
- `Grid.globalClue?: { label: string; wordLengths: number[] }` — `wordLengths` drives the cell structure.
- `Cell.letter?.number?: number` — 1-based integer manually set by creator; consecutive 1..N across all words.
- For word at index `i`, letter positions in the global clue are `(sum of previous wordLengths + 1)` to `(sum including current wordLength)`.
- A cell is "filled" if the corresponding main-grid cell has a non-empty `letter.value`.

- [ ] **Step 1: Create the TypeScript component**

```typescript
// global-clue-preview.component.ts
import { Component, computed, inject } from '@angular/core';
import { GridSelectorService } from '@application/service/grid-selector.service';

@Component({
  selector: 'cocro-global-clue-preview',
  standalone: true,
  templateUrl: './global-clue-preview.component.html',
  styleUrls: ['./global-clue-preview.component.scss'],
})
export class GlobalCluePreviewComponent {
  private readonly selectorService = inject(GridSelectorService);

  /**
   * Returns an array of words, each word being an array of letter strings.
   * Empty string means the corresponding numbered cell has no letter yet.
   * If no global clue or no word lengths defined, returns [].
   */
  readonly previewWords = computed<string[][]>(() => {
    const grid = this.selectorService.grid();
    const wordLengths = grid.globalClue?.wordLengths;
    if (!wordLengths?.length) return [];

    // Build number → letter value map from all cells
    const letterByNumber = new Map<number, string>();
    for (const cell of grid.cells) {
      if (cell.letter?.number != null && cell.letter.value) {
        letterByNumber.set(cell.letter.number, cell.letter.value);
      }
    }

    const words: string[][] = [];
    let offset = 0;
    for (const length of wordLengths) {
      const word: string[] = [];
      for (let i = 1; i <= length; i++) {
        word.push(letterByNumber.get(offset + i) ?? '');
      }
      offset += length;
      words.push(word);
    }
    return words;
  });
}
```

- [ ] **Step 2: Create the HTML template**

```html
<!-- global-clue-preview.component.html -->
@if (previewWords().length) {
  <div class="gcp">
    <div class="gcp__row">
      @for (word of previewWords(); track $index) {
        <div class="gcp__word">
          @for (letter of word; track $index) {
            <span class="gcp__cell">{{ letter }}</span>
          }
        </div>
      }
    </div>
  </div>
}
```

- [ ] **Step 3: Create the SCSS file**

```scss
// global-clue-preview.component.scss
.gcp {
  display: flex;
  justify-content: center;
  padding: 0.5rem 0;
  margin-bottom: 0.25rem;
}

.gcp__row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  justify-content: center;
}

.gcp__word {
  display: flex;
}

.gcp__cell {
  width: 22px;
  height: 22px;
  border: 1.5px solid var(--color-ink-muted);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 0.7rem;
  font-weight: 700;
  font-family: var(--font-serif);
  background: white;
  color: var(--color-ink);
  text-transform: uppercase;

  // Shared border between adjacent cells in the same word
  & + & {
    border-left: none;
  }
}
```

- [ ] **Step 4: Verify build (component alone, not yet integrated)**

```bash
cd /Users/oscar_mallet/Documents/cocro/cocro-web && npx ng build 2>&1 | tail -5
```

Expected: 0 errors. If the component is not imported anywhere yet, the build still succeeds.

- [ ] **Step 5: Commit**

```bash
git add cocro-web/src/app/presentation/features/grid/editor/global-clue-preview/
git commit -m "feat(ui): add GlobalCluePreviewComponent for enigme globale mini-grid"
```

---

### Task 3: Integrate preview into grid editor

**Files:**
- Modify: `cocro-web/src/app/presentation/features/grid/editor/grid-editor/grid-editor.component.ts`
- Modify: `cocro-web/src/app/presentation/features/grid/editor/grid-editor/grid-editor.component.html`

**Context:**
- `showGlobalClue()` is a `signal<boolean>` on `GridEditorComponent` — the preview should only appear when the global clue panel is active.
- The preview goes inside `<div class="grid-wrapper">`, above `<cocro-grid />`. The `.grid-wrapper` is already `display: flex; flex-direction: column; align-items: center;` so the preview will naturally sit centered above the grid.

- [ ] **Step 1: Add import to grid-editor.component.ts**

Add `GlobalCluePreviewComponent` to the imports array:

```typescript
// Add to existing imports in the file:
import { GlobalCluePreviewComponent } from '@presentation/features/grid/editor/global-clue-preview/global-clue-preview.component';

// Add to the @Component imports array (alongside GlobalClueEditorComponent):
GlobalCluePreviewComponent,
```

The full `imports` array after modification:

```typescript
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
```

- [ ] **Step 2: Add the component to the template**

In `grid-editor.component.html`, inside `<div class="grid-wrapper">`, add the preview above `<cocro-grid />`:

```html
<div class="grid-wrapper">
  @if (showGlobalClue()) {
    <cocro-global-clue-preview />
  }
  <cocro-grid />
</div>
```

- [ ] **Step 3: Verify build**

```bash
cd /Users/oscar_mallet/Documents/cocro/cocro-web && npx ng build 2>&1 | tail -5
```

Expected: 0 errors.

- [ ] **Step 4: Smoke test in browser**

```bash
cd /Users/oscar_mallet/Documents/cocro/cocro-web && npx ng serve --port 4200
```

Manual verification:
1. Open http://localhost:4200 and navigate to the grid editor.
2. Click "Activer énigme globale" — the global clue panel opens and the mini-grid appears above the main grid (empty cells).
3. In the editor, add a word with stepper (e.g., MOT 1 = 4 letters). The mini-grid shows 4 empty cells in a row.
4. In the main grid, set `letter.number = 1` on a cell and type a letter. The corresponding cell in the mini-grid now shows that letter.
5. Deactivate the global clue — the mini-grid disappears.

- [ ] **Step 5: Commit**

```bash
git add cocro-web/src/app/presentation/features/grid/editor/grid-editor/grid-editor.component.ts
git add cocro-web/src/app/presentation/features/grid/editor/grid-editor/grid-editor.component.html
git commit -m "feat(editor): integrate GlobalCluePreviewComponent above grid"
```
