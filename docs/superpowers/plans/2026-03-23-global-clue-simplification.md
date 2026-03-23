# Global Clue Model Simplification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace `globalClueWords: List<List<Int>>` with `globalClueWordLengths: List<Int>` across the full stack, add backend validation, and update the Angular editor UI to use length steppers instead of free-text number sequences.

**Architecture:** Three independent subsystems are touched in order: (1) `cocro-shared` domain — rename the field and add error types; (2) `cocro-bff` — update DTOs, persistence, mappers, and add validation engine method; (3) `cocro-angular` — update domain model, DTO mapping, and rewrite the GlobalClueEditor component.

**Tech Stack:** Kotlin multiplatform (`cocro-shared`), Spring Boot 3.2 + Kotlin (`cocro-bff`), Angular 20 + Signals (`cocro-angular`), Gradle, JUnit 5 + Mockito-Kotlin, Jasmine/Karma (Angular).

---

## File Map

| File | Change |
|------|--------|
| `cocro-shared/.../error/ErrorCode.kt` | Add 5 new `ErrorCode` entries |
| `cocro-shared/.../grid/error/GridError.kt` | Add 5 new `GridError` subtypes |
| `cocro-shared/.../grid/model/GridMetadata.kt` | Rename `globalClueWords` → `globalClueWordLengths: List<Int>?` |
| `cocro-bff/.../persistence/mongo/grid/document/GridMetadataDocument.kt` | Rename field |
| `cocro-bff/.../application/grid/dto/GridDto.kt` | Rename field in `SubmitGridDto` and `PatchGridDto` |
| `cocro-bff/.../application/grid/mapper/GridMapper.kt` | Update field references |
| `cocro-bff/.../infrastructure/persistence/mongo/grid/mapper/GridDocumentMapper.kt` | Update field references |
| `cocro-bff/.../application/grid/validation/dsl/engine/GridValidationEngine.kt` | Add `validateGlobalClue()` method |
| `cocro-bff/.../application/grid/validation/ValidateSubmitGridSchema.kt` | Call `validateGlobalClue()` |
| `cocro-bff/.../application/grid/validation/ValidatePatchGridSchema.kt` | Call `validateGlobalClue()` |
| `cocro-bff/src/test/.../usecase/SubmitGridUseCaseTest.kt` | Add global clue validation tests |
| `cocro-angular/.../domain/models/grid.model.ts` | `words: number[][]` → `wordLengths: number[]` |
| `cocro-angular/.../application/dto/grid.dto.ts` | `globalClueWords` → `globalClueWordLengths` |
| `cocro-angular/.../global-clue-editor/global-clue-editor.component.ts` | Rewrite logic for wordLengths |
| `cocro-angular/.../global-clue-editor/global-clue-editor.component.html` | Replace text input + preview with stepper |
| `cocro-angular/.../global-clue-editor/global-clue-editor.component.scss` | Replace word-input/preview styles with stepper styles |

---

## Task 1: cocro-shared — Rename field in GridMetadata

**Files:**
- Modify: `cocro-shared/src/commonMain/kotlin/com/cocro/kernel/grid/model/GridMetadata.kt`

- [ ] **Step 1: Rename the field**

  Open `GridMetadata.kt`. Change:
  ```kotlin
  val globalClueWords: List<List<Int>>? = null,
  ```
  to:
  ```kotlin
  val globalClueWordLengths: List<Int>? = null,
  ```

- [ ] **Step 2: Verify the build catches all usages**

  ```bash
  cd /path/to/cocro && ./gradlew cocro-shared:build 2>&1 | grep -i error
  ```
  Expected: compilation errors in `cocro-bff` referencing `globalClueWords` — this is correct, they'll be fixed in Task 3.

- [ ] **Step 3: Commit**

  ```bash
  git add cocro-shared/src/commonMain/kotlin/com/cocro/kernel/grid/model/GridMetadata.kt
  git commit -m "refactor(shared): rename globalClueWords → globalClueWordLengths in GridMetadata"
  ```

---

## Task 2: cocro-shared — Add ErrorCode entries and GridError subtypes

**Files:**
- Modify: `cocro-shared/src/commonMain/kotlin/com/cocro/kernel/common/error/ErrorCode.kt`
- Modify: `cocro-shared/src/commonMain/kotlin/com/cocro/kernel/grid/error/GridError.kt`

- [ ] **Step 1: Add ErrorCode entries**

  Open `ErrorCode.kt`. After the last `GRID_*` entry (currently `GRID_INVALID_SAFE_STRING`), add:

  ```kotlin
  GRID_GLOBAL_CLUE_LABEL_MISSING("Global clue label is required when word lengths are specified", 400),
  GRID_GLOBAL_CLUE_NO_WORDS("Global clue must have at least one word", 400),
  GRID_GLOBAL_CLUE_WORD_LENGTH_INVALID("Global clue word length must be at least 1", 400),
  GRID_GLOBAL_CLUE_LETTER_COUNT_MISMATCH("Global clue letter count does not match indexed cell count", 400),
  GRID_GLOBAL_CLUE_NUMBERING_INVALID("Indexed cell numbers must form a consecutive sequence starting at 1", 400),
  ```

- [ ] **Step 2: Add GridError subtypes**

  Open `GridError.kt`. At the end of the sealed interface, before the closing `}`, add:

  ```kotlin
  object GlobalClueLabelMissing : GridError {
      override val errorCode = ErrorCode.GRID_GLOBAL_CLUE_LABEL_MISSING
  }

  object GlobalClueNoWords : GridError {
      override val errorCode = ErrorCode.GRID_GLOBAL_CLUE_NO_WORDS
  }

  data class GlobalClueWordLengthInvalid(val index: Int) : GridError {
      override val errorCode = ErrorCode.GRID_GLOBAL_CLUE_WORD_LENGTH_INVALID

      override fun context() = mapOf("wordIndex" to index.toString())
  }

  data class GlobalClueLetterCountMismatch(val expected: Int, val actual: Int) : GridError {
      override val errorCode = ErrorCode.GRID_GLOBAL_CLUE_LETTER_COUNT_MISMATCH

      override fun context() = mapOf(
          "expected" to expected.toString(),
          "actual" to actual.toString(),
      )
  }

  object GlobalClueNumberingInvalid : GridError {
      override val errorCode = ErrorCode.GRID_GLOBAL_CLUE_NUMBERING_INVALID
  }
  ```

- [ ] **Step 3: Build shared**

  ```bash
  ./gradlew cocro-shared:build 2>&1 | grep -i error
  ```
  Expected: no errors in cocro-shared itself.

- [ ] **Step 4: Commit**

  ```bash
  git add cocro-shared/src/commonMain/kotlin/com/cocro/kernel/common/error/ErrorCode.kt \
          cocro-shared/src/commonMain/kotlin/com/cocro/kernel/grid/error/GridError.kt
  git commit -m "feat(shared): add ErrorCode and GridError subtypes for global clue validation"
  ```

---

## Task 3: cocro-bff — Update DTOs, persistence doc, and mappers

**Files:**
- Modify: `cocro-bff/src/main/kotlin/com/cocro/application/grid/dto/GridDto.kt`
- Modify: `cocro-bff/src/main/kotlin/com/cocro/infrastructure/persistence/mongo/grid/document/GridMetadataDocument.kt`
- Modify: `cocro-bff/src/main/kotlin/com/cocro/application/grid/mapper/GridMapper.kt`
- Modify: `cocro-bff/src/main/kotlin/com/cocro/infrastructure/persistence/mongo/grid/mapper/GridDocumentMapper.kt`

- [ ] **Step 1: Update SubmitGridDto and PatchGridDto**

  In `GridDto.kt`, in `SubmitGridDto` change:
  ```kotlin
  val globalClueWords: List<List<Int>>? = null,
  ```
  to:
  ```kotlin
  val globalClueWordLengths: List<Int>? = null,
  ```

  Same change in `PatchGridDto`.

- [ ] **Step 2: Update GridMetadataDocument**

  In `GridMetadataDocument.kt` change:
  ```kotlin
  val globalClueWords: List<List<Int>>? = null,
  ```
  to:
  ```kotlin
  val globalClueWordLengths: List<Int>? = null,
  ```

- [ ] **Step 3: Update GridMapper**

  In `GridMapper.kt`, in `SubmitGridDto.toDomain()`, change:
  ```kotlin
  globalClueWords = this.globalClueWords,
  ```
  to:
  ```kotlin
  globalClueWordLengths = this.globalClueWordLengths,
  ```

  In `PatchGridDto.applyPatchTo()`, change:
  ```kotlin
  globalClueWords = this.globalClueWords ?: grid.metadata.globalClueWords,
  ```
  to:
  ```kotlin
  globalClueWordLengths = this.globalClueWordLengths ?: grid.metadata.globalClueWordLengths,
  ```

- [ ] **Step 4: Update GridDocumentMapper**

  In `GridDocumentMapper.kt`, in `Grid.toDocument()`, change:
  ```kotlin
  globalClueWords = metadata.globalClueWords,
  ```
  to:
  ```kotlin
  globalClueWordLengths = metadata.globalClueWordLengths,
  ```

  In `GridDocument.toDomain()`, change:
  ```kotlin
  globalClueWords = metadata.globalClueWords,
  ```
  to:
  ```kotlin
  globalClueWordLengths = metadata.globalClueWordLengths,
  ```

- [ ] **Step 5: Build bff**

  ```bash
  ./gradlew cocro-bff:build 2>&1 | grep -i error
  ```
  Expected: 0 errors.

- [ ] **Step 6: Commit**

  ```bash
  git add cocro-bff/src/main/kotlin/com/cocro/application/grid/dto/GridDto.kt \
          cocro-bff/src/main/kotlin/com/cocro/infrastructure/persistence/mongo/grid/document/GridMetadataDocument.kt \
          cocro-bff/src/main/kotlin/com/cocro/application/grid/mapper/GridMapper.kt \
          cocro-bff/src/main/kotlin/com/cocro/infrastructure/persistence/mongo/grid/mapper/GridDocumentMapper.kt
  git commit -m "refactor(bff): rename globalClueWords → globalClueWordLengths in DTOs, documents, and mappers"
  ```

---

## Task 4: cocro-bff — Add global clue validation

**Files:**
- Modify: `cocro-bff/src/main/kotlin/com/cocro/application/grid/validation/dsl/engine/GridValidationEngine.kt`
- Modify: `cocro-bff/src/main/kotlin/com/cocro/application/grid/validation/ValidateSubmitGridSchema.kt`
- Modify: `cocro-bff/src/main/kotlin/com/cocro/application/grid/validation/ValidatePatchGridSchema.kt`
- Test: `cocro-bff/src/test/kotlin/com/cocro/application/grid/usecase/SubmitGridUseCaseTest.kt`

- [ ] **Step 1: Write failing tests first**

  Open `SubmitGridUseCaseTest.kt`. Add these tests after the existing ones. The `validDto()` helper already exists — build on it:

  ```kotlin
  private fun validDtoWithNumberedCells(
      wordLengths: List<Int>,
      label: String = "Qui suis-je ?",
  ): SubmitGridDto {
      val base = validDto()
      // Number consecutive cells starting at 1
      var n = 1
      val cells = base.cells.map { cell ->
          if (cell.type == CellType.LETTER && n <= wordLengths.sum()) {
              cell.copy(number = n++)
          } else {
              cell
          }
      }
      return base.copy(
          cells = cells,
          globalClueLabel = label,
          globalClueWordLengths = wordLengths,
      )
  }

  @Test
  fun `submit with valid global clue succeeds`() {
      whenever(currentUserProvider.currentUserOrNull()).thenReturn(authenticatedUser)
      whenever(gridIdGenerator.generateId()).thenReturn(GridShareCode("ABCDEF"))
      whenever(gridRepository.findByHashLetters(any())).thenReturn(null)
      whenever(gridRepository.save(any())).thenAnswer { it.arguments[0] as com.cocro.kernel.grid.model.Grid }

      val dto = validDtoWithNumberedCells(listOf(2, 1))
      val result = useCase.execute(dto)

      assertThat(result).isInstanceOf(CocroResult.Success::class.java)
  }

  @Test
  fun `submit with global clue but missing label returns GlobalClueLabelMissing`() {
      val dto = validDtoWithNumberedCells(listOf(2, 1), label = "")
      val result = useCase.execute(dto)

      assertThat(result).isInstanceOf(CocroResult.Error::class.java)
      val errors = (result as CocroResult.Error).errors
      assertThat(errors).anyMatch { it is GridError.GlobalClueLabelMissing }
  }

  @Test
  fun `submit with empty word lengths list returns GlobalClueNoWords`() {
      whenever(currentUserProvider.currentUserOrNull()).thenReturn(authenticatedUser)
      val dto = validDto().copy(
          globalClueLabel = "Qui suis-je ?",
          globalClueWordLengths = emptyList(),
      )
      val result = useCase.execute(dto)

      assertThat(result).isInstanceOf(CocroResult.Error::class.java)
      val errors = (result as CocroResult.Error).errors
      assertThat(errors).anyMatch { it is GridError.GlobalClueNoWords }
  }

  @Test
  fun `submit with word length of 0 returns GlobalClueWordLengthInvalid`() {
      val dto = validDtoWithNumberedCells(listOf(0, 2))
      val result = useCase.execute(dto)

      assertThat(result).isInstanceOf(CocroResult.Error::class.java)
      val errors = (result as CocroResult.Error).errors
      assertThat(errors).anyMatch { it is GridError.GlobalClueWordLengthInvalid && it.index == 0 }
  }

  @Test
  fun `submit with letter count mismatch returns GlobalClueLetterCountMismatch`() {
      // Request 5 letters but only number 3 cells
      val base = validDto()
      val cells = base.cells.mapIndexed { i, cell ->
          if (i < 3 && cell.type == CellType.LETTER) cell.copy(number = i + 1) else cell
      }
      val dto = base.copy(
          cells = cells,
          globalClueLabel = "Qui suis-je ?",
          globalClueWordLengths = listOf(3, 2), // sum = 5
      )
      val result = useCase.execute(dto)

      assertThat(result).isInstanceOf(CocroResult.Error::class.java)
      val errors = (result as CocroResult.Error).errors
      assertThat(errors).anyMatch { it is GridError.GlobalClueLetterCountMismatch }
  }

  @Test
  fun `submit with non-consecutive numbering returns GlobalClueNumberingInvalid`() {
      val base = validDto()
      // Number 3 cells but with a gap: 1, 2, 4 (missing 3)
      val numbers = listOf(1, 2, 4)
      var idx = 0
      val cells = base.cells.map { cell ->
          if (cell.type == CellType.LETTER && idx < numbers.size) {
              cell.copy(number = numbers[idx++])
          } else {
              cell
          }
      }
      val dto = base.copy(
          cells = cells,
          globalClueLabel = "Qui suis-je ?",
          globalClueWordLengths = listOf(2, 1), // sum = 3
      )
      val result = useCase.execute(dto)

      assertThat(result).isInstanceOf(CocroResult.Error::class.java)
      val errors = (result as CocroResult.Error).errors
      assertThat(errors).anyMatch { it is GridError.GlobalClueNumberingInvalid }
  }
  ```

- [ ] **Step 2: Run tests — expect failures**

  ```bash
  TESTCONTAINERS_RYUK_DISABLED=true ./gradlew cocro-bff:test --tests "*.SubmitGridUseCaseTest" --no-daemon 2>&1 | tail -30
  ```
  Expected: new tests fail because `validateGlobalClue()` doesn't exist yet.

- [ ] **Step 3: Add validateGlobalClue() to GridValidationEngine**

  Open `GridValidationEngine.kt`. At the end of the class (before the closing `}`), add:

  ```kotlin
  // -------------------------------------------------------------------------
  // GLOBAL CLUE
  // -------------------------------------------------------------------------

  fun validateGlobalClue(
      globalClueLabel: String?,
      globalClueWordLengths: List<Int>?,
      cells: List<CellDto>?,
  ) {
      if (globalClueLabel == null && globalClueWordLengths == null) return

      if (globalClueWordLengths != null && globalClueLabel.isNullOrBlank()) {
          errors += GridError.GlobalClueLabelMissing
      }

      if (globalClueWordLengths != null) {
          if (globalClueWordLengths.isEmpty()) {
              errors += GridError.GlobalClueNoWords
              return
          }

          globalClueWordLengths.forEachIndexed { i, len ->
              if (len < 1) errors += GridError.GlobalClueWordLengthInvalid(i)
          }

          if (cells != null) {
              val numberedCells = cells
                  .filter { it.type == CellType.LETTER && it.number != null }
                  .map { it.number!! }

              val expected = globalClueWordLengths.sum()
              if (numberedCells.size != expected) {
                  errors += GridError.GlobalClueLetterCountMismatch(expected, numberedCells.size)
              } else {
                  val sorted = numberedCells.sorted()
                  if (sorted != (1..numberedCells.size).toList()) {
                      errors += GridError.GlobalClueNumberingInvalid
                  }
              }
          }
      }
  }
  ```

  You will also need this import at the top of the file:
  ```kotlin
  import com.cocro.kernel.grid.error.GridError
  ```
  (Check if it's already present — `GridError` is already referenced in the file.)

- [ ] **Step 4: Wire into ValidateSubmitGridSchema and ValidatePatchGridSchema**

  The `validateGrid(dto) { ... }` block receiver is `GridValidationDsl`, not `GridValidationEngine` directly. Add a delegating method to `GridValidationDsl.kt`:

  In `GridValidationDsl.kt`, add this import at the top (it is **absent** and **required**):
  ```kotlin
  import com.cocro.application.grid.dto.CellDto
  ```

  Then add this method to the `GridValidationDsl` class body:
  ```kotlin
  fun globalClue(label: String?, wordLengths: List<Int>?, cells: List<CellDto>?) =
      engine.validateGlobalClue(label, wordLengths, cells)
  ```

  In `ValidateSubmitGridSchema.kt`, add inside the `validateGrid(dto) { ... }` block, after the `cells { }` block:
  ```kotlin
  globalClue(dto.globalClueLabel, dto.globalClueWordLengths, dto.cells)
  ```

  In `ValidatePatchGridSchema.kt`, same addition after its `cells { }` block:
  ```kotlin
  globalClue(dto.globalClueLabel, dto.globalClueWordLengths, dto.cells)
  ```

- [ ] **Step 5: Run tests — expect passing**

  ```bash
  TESTCONTAINERS_RYUK_DISABLED=true ./gradlew cocro-bff:test --tests "*.SubmitGridUseCaseTest" --no-daemon 2>&1 | tail -30
  ```
  Expected: all tests pass.

- [ ] **Step 6: Run full bff test suite**

  ```bash
  TESTCONTAINERS_RYUK_DISABLED=true ./gradlew cocro-bff:test --no-daemon 2>&1 | tail -20
  ```
  Expected: BUILD SUCCESSFUL, 0 failures.

- [ ] **Step 7: Commit**

  ```bash
  git add cocro-bff/src/main/kotlin/com/cocro/application/grid/validation/dsl/engine/GridValidationEngine.kt \
          cocro-bff/src/main/kotlin/com/cocro/application/grid/validation/dsl/engine/GridValidationDsl.kt \
          cocro-bff/src/main/kotlin/com/cocro/application/grid/validation/ValidateSubmitGridSchema.kt \
          cocro-bff/src/main/kotlin/com/cocro/application/grid/validation/ValidatePatchGridSchema.kt \
          cocro-bff/src/test/kotlin/com/cocro/application/grid/usecase/SubmitGridUseCaseTest.kt
  git commit -m "feat(bff): add global clue validation (label, word lengths, cell count, numbering)"
  ```

---

## Task 5: Angular — Update domain model and DTO

**Files:**
- Modify: `cocro-angular/src/app/domain/models/grid.model.ts`
- Modify: `cocro-angular/src/app/application/dto/grid.dto.ts`

- [ ] **Step 1: Update GlobalClue interface**

  In `grid.model.ts`, change:
  ```ts
  export interface GlobalClue {
    label: string;
    words: number[][];
  }
  ```
  to:
  ```ts
  export interface GlobalClue {
    label: string;
    wordLengths: number[];
  }
  ```

- [ ] **Step 2: Update DTO interfaces and mapping**

  In `grid.dto.ts`:

  In `SubmitGridRequest`, change:
  ```ts
  globalClueWords?: number[][];
  ```
  to:
  ```ts
  globalClueWordLengths?: number[];
  ```

  In `PatchGridRequest`, same change.

  In `grid-editor.component.ts` (where `SubmitGridRequest` is built), the mapping currently uses:
  ```ts
  globalClueWords: grid.globalClue?.words,
  ```
  Change to:
  ```ts
  globalClueWordLengths: grid.globalClue?.wordLengths,
  ```

- [ ] **Step 3: Build Angular**

  ```bash
  cd cocro-angular && npx ng build 2>&1 | grep -i error
  ```
  Expected: compilation errors in `GlobalClueEditorComponent` referencing `.words` — these will be fixed in Task 6.

- [ ] **Step 4: Commit**

  ```bash
  git add cocro-angular/src/app/domain/models/grid.model.ts \
          cocro-angular/src/app/application/dto/grid.dto.ts \
          cocro-angular/src/app/presentation/features/grid/editor/grid-editor/grid-editor.component.ts
  git commit -m "refactor(angular): rename GlobalClue.words → wordLengths, update DTO"
  ```

---

## Task 6: Angular — Rewrite GlobalClueEditor component

**Files:**
- Modify: `cocro-angular/src/app/presentation/features/grid/editor/global-clue-editor/global-clue-editor.component.ts`
- Modify: `cocro-angular/src/app/presentation/features/grid/editor/global-clue-editor/global-clue-editor.component.html`
- Modify: `cocro-angular/src/app/presentation/features/grid/editor/global-clue-editor/global-clue-editor.component.scss`

- [ ] **Step 1: Rewrite the component TypeScript**

  Replace the entire content of `global-clue-editor.component.ts`:

  ```ts
  import { Component, computed, inject } from '@angular/core';
  import { FormsModule } from '@angular/forms';
  import { GridSelectorService } from '@application/service/grid-selector.service';
  import { GlobalClue } from '@domain/models/grid.model';

  @Component({
    selector: 'cocro-global-clue-editor',
    standalone: true,
    imports: [FormsModule],
    templateUrl: './global-clue-editor.component.html',
    styleUrls: ['./global-clue-editor.component.scss'],
  })
  export class GlobalClueEditorComponent {
    readonly selectorService = inject(GridSelectorService);

    readonly numberedCellCount = computed(() =>
      this.selectorService.grid().cells.filter(c => c.letter?.number != null).length
    );

    readonly totalRequested = computed(() =>
      (this.globalClue?.wordLengths ?? []).reduce((a, b) => a + b, 0)
    );

    readonly countMismatch = computed(() =>
      (this.globalClue?.wordLengths?.length ?? 0) > 0 &&
      this.totalRequested() !== this.numberedCellCount()
    );

    get globalClue(): GlobalClue | undefined {
      return this.selectorService.grid().globalClue;
    }

    updateLabel(label: string): void {
      const current = this.globalClue;
      this.selectorService.updateGlobalClue({
        label,
        wordLengths: current?.wordLengths ?? [],
      });
    }

    incrementLength(index: number): void {
      const current = this.globalClue;
      if (!current) return;
      const wordLengths = [...current.wordLengths];
      wordLengths[index] = (wordLengths[index] ?? 1) + 1;
      this.selectorService.updateGlobalClue({ ...current, wordLengths });
    }

    decrementLength(index: number): void {
      const current = this.globalClue;
      if (!current) return;
      const wordLengths = [...current.wordLengths];
      if ((wordLengths[index] ?? 1) <= 1) return;
      wordLengths[index] = wordLengths[index] - 1;
      this.selectorService.updateGlobalClue({ ...current, wordLengths });
    }

    addWord(): void {
      const current = this.globalClue;
      const wordLengths = current ? [...current.wordLengths, 1] : [1];
      this.selectorService.updateGlobalClue({
        label: current?.label ?? '',
        wordLengths,
      });
    }

    removeWord(index: number): void {
      const current = this.globalClue;
      if (!current) return;
      const wordLengths = current.wordLengths.filter((_, i) => i !== index);
      this.selectorService.updateGlobalClue({ ...current, wordLengths });
    }
  }
  ```

- [ ] **Step 2: Rewrite the template**

  Replace the entire content of `global-clue-editor.component.html`:

  ```html
  <div class="gce">

    <div class="gce__question">
      <span class="gce__q-mark">?</span>
      <input
        class="gce__label-input"
        type="text"
        maxlength="200"
        placeholder="L'énigme à résoudre…"
        [ngModel]="globalClue?.label ?? ''"
        (ngModelChange)="updateLabel($event)"
      />
    </div>

    @if (globalClue?.wordLengths?.length) {
      <div class="gce__words">
        @for (len of globalClue!.wordLengths; track $index) {
          <div class="gce__word">
            <span class="gce__word-label">MOT {{ $index + 1 }}</span>
            <div class="gce__stepper">
              <button type="button" class="gce__step-btn" (click)="decrementLength($index)" [disabled]="len <= 1">−</button>
              <span class="gce__step-value">{{ len }}</span>
              <button type="button" class="gce__step-btn" (click)="incrementLength($index)">+</button>
            </div>
            <button type="button" class="gce__remove" (click)="removeWord($index)" title="Supprimer">×</button>
          </div>
        }
      </div>
    }

    <button type="button" class="gce__add" (click)="addWord()">
      <span class="gce__add-icon">+</span>
      Ajouter un mot
    </button>

    @if (countMismatch()) {
      <p class="gce__warning">
        ⚠ {{ totalRequested() }} lettre{{ totalRequested() > 1 ? 's' : '' }} demandée{{ totalRequested() > 1 ? 's' : '' }}
        / {{ numberedCellCount() }} indexée{{ numberedCellCount() > 1 ? 's' : '' }}
      </p>
    }

  </div>
  ```

- [ ] **Step 3: Update the SCSS**

  In `global-clue-editor.component.scss`:

  Remove the `.gce__word-input`, `.gce__preview`, `.gce__cell`, `.gce__cell--unresolved` blocks entirely (they are dead code).

  Change `.gce__word-header` to `.gce__word` with a flex row layout, and add stepper styles. Replace with:

  ```scss
  // ── Words list ────────────────────────────────────────────────────────────────

  .gce__words {
    display: flex;
    flex-direction: column;
    gap: var(--space-sm);
  }

  .gce__word {
    display: flex;
    align-items: center;
    gap: var(--space-sm);
  }

  .gce__word-label {
    font-family: var(--font-label);
    font-size: var(--text-xs);
    font-weight: 700;
    letter-spacing: 0.1em;
    text-transform: uppercase;
    color: var(--color-gold);
    white-space: nowrap;
    flex-shrink: 0;
    min-width: 3.2rem;
  }

  // ── Stepper ───────────────────────────────────────────────────────────────────

  .gce__stepper {
    display: flex;
    align-items: center;
    gap: var(--space-xs);
    flex: 1;
  }

  .gce__step-btn {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 1.75rem;
    height: 1.75rem;
    background: none;
    border: 1px solid var(--color-border);
    border-radius: 4px;
    color: var(--color-ink);
    font-size: var(--text-base);
    line-height: 1;
    cursor: pointer;
    transition: background-color var(--transition-smooth), border-color var(--transition-smooth);

    &:hover:not(:disabled) {
      background-color: var(--color-forest-tint);
      border-color: var(--color-forest);
    }

    &:disabled {
      opacity: 0.35;
      cursor: default;
    }
  }

  .gce__step-value {
    font-family: var(--font-mono);
    font-size: var(--text-base);
    font-weight: 700;
    color: var(--color-ink);
    min-width: 1.5rem;
    text-align: center;
  }

  // ── Validation warning ────────────────────────────────────────────────────────

  .gce__warning {
    font-family: var(--font-label);
    font-size: var(--text-xs);
    color: var(--color-gold);
    margin: 0;
    padding: var(--space-xs) var(--space-sm);
    background-color: rgba(var(--color-gold-rgb, 180, 130, 0), 0.08);
    border-radius: 4px;
    border: 1px solid rgba(var(--color-gold-rgb, 180, 130, 0), 0.2);
  }
  ```

  Keep the `.gce`, `.gce__question`, `.gce__q-mark`, `.gce__label-input`, `.gce__remove`, and `.gce__add` blocks unchanged.

- [ ] **Step 4: Build Angular — expect 0 errors**

  ```bash
  cd cocro-angular && npx ng build 2>&1 | grep -iE "error|warning"
  ```
  Expected: 0 errors, 0 warnings.

- [ ] **Step 5: Commit**

  ```bash
  git add cocro-angular/src/app/presentation/features/grid/editor/global-clue-editor/
  git commit -m "feat(angular): rewrite GlobalClueEditor with word-length steppers and count validation"
  ```

---

## Task 7: Full stack verification

- [ ] **Step 1: Run all backend tests**

  ```bash
  TESTCONTAINERS_RYUK_DISABLED=true ./gradlew test --no-daemon 2>&1 | tail -20
  ```
  Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Build Angular**

  ```bash
  cd cocro-angular && npx ng build 2>&1 | tail -10
  ```
  Expected: 0 errors.

- [ ] **Step 3: Smoke test the editor manually (optional)**

  ```bash
  cd cocro-angular && npx ng serve
  ```
  Open `http://localhost:4200`, navigate to the grid editor, toggle "Énigme globale", verify:
  - Label input is present
  - "Ajouter un mot" creates a word row with `MOT 1`, `−`, `1`, `+`, `×`
  - `+` / `−` adjust the length (min 1)
  - Warning banner appears when numbered cell count ≠ sum of lengths

- [ ] **Step 4: Final commit (if any cleanup needed)**

  ```bash
  git add -p
  git commit -m "chore: cleanup after global clue simplification"
  ```
