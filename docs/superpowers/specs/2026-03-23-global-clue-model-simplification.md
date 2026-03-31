# Spec — Global Clue Model Simplification

**Date:** 2026-03-23
**Status:** Approved

## Context

The current `GlobalClue` model stores explicit cell number sequences per word (`words: List<List<Int>>`). Since cell numbers are always consecutive (1..N), this structure is redundant — the same information is fully captured by word lengths alone. This spec replaces `words: number[][]` with `wordLengths: number[]` across the stack, and adds proper backend validation for the global clue.

## Scope

- `cocro-shared`: domain model, new error types, new ErrorCode entries
- `cocro-bff`: DTOs, persistence documents, mappers, validation engine
- `cocro-web`: domain model, DTO, GlobalClueEditor component

No data migration needed — `globalClueWords` is `null` for all existing grids in MongoDB.

---

## 1. Data Model Changes

### cocro-shared — `GridMetadata`

```kotlin
data class GridMetadata(
    val author: UserId,
    val reference: String?,
    val description: String?,
    val difficulty: String = "NONE",
    val globalClueLabel: String? = null,
    val globalClueWordLengths: List<Int>? = null,  // was: globalClueWords: List<List<Int>>?
)
```

### cocro-bff — `GridMetadataDocument`

```kotlin
data class GridMetadataDocument(
    val author: String,
    val reference: String?,
    val description: String?,
    val difficulty: String = "NONE",
    val globalClueLabel: String? = null,
    val globalClueWordLengths: List<Int>? = null,  // was: globalClueWords: List<List<Int>>?
)
```

### cocro-bff — `SubmitGridDto` and `PatchGridDto`

```kotlin
val globalClueWordLengths: List<Int>? = null  // was: globalClueWords: List<List<Int>>?
```

### cocro-web — `grid.model.ts`

```ts
export interface GlobalClue {
  label: string;
  wordLengths: number[];  // was: words: number[][]
}
```

### cocro-web — `grid.dto.ts`

```ts
// In SubmitGridRequest and PatchGridRequest:
globalClueWordLengths?: number[];  // was: globalClueWords?: number[][]

// Mapping helper (replaces old globalClueWords mapping):
globalClueLabel: grid.globalClue?.label,
globalClueWordLengths: grid.globalClue?.wordLengths,
```

---

## 2. Mappers

### cocro-bff — `GridMapper.kt`

```kotlin
// SubmitGridDto.toDomain():
globalClueLabel = this.globalClueLabel,
globalClueWordLengths = this.globalClueWordLengths,

// PatchGridDto.applyPatchTo():
globalClueLabel = this.globalClueLabel ?: grid.metadata.globalClueLabel,
globalClueWordLengths = this.globalClueWordLengths ?: grid.metadata.globalClueWordLengths,
```

### cocro-bff — `GridDocumentMapper.kt`

```kotlin
// Grid.toDocument():
globalClueWordLengths = metadata.globalClueWordLengths,

// GridDocument.toDomain():
globalClueWordLengths = metadata.globalClueWordLengths,
```

---

## 3. Frontend — GlobalClueEditor UI

Replace the free-text cell number input per word with a length stepper (+/−).

### Layout

```
┌─────────────────────────────────────┐
│ ? [L'énigme à résoudre…           ] │
│                                     │
│  MOT 1   [−] 4 [+]   [×]           │
│  MOT 2   [−] 3 [+]   [×]           │
│                                     │
│  [+ Ajouter un mot]                 │
│                                     │
│  ⚠ 7 lettres demandées / 5 indexées │
└─────────────────────────────────────┘
```

### Behaviour

- Each word row: `−` / `+` buttons adjust length (min 1, no enforced max)
- `×` removes the word
- "Ajouter un mot" appends a word with length 1
- No letter-by-letter preview (removed — no longer meaningful)
- Validation banner is shown only when `sum(wordLengths) !== numberedCellCount`; it does not block submission

### Front validation (non-blocking)

```ts
const numberedCellCount = grid.cells.filter(c => c.letter?.number != null).length;
const sum = wordLengths.reduce((a, b) => a + b, 0);
// Show warning if sum !== numberedCellCount
```

Cell `letter.number` is a 1-based integer manually assigned by the creator. Consecutive numbering is enforced by backend validation, not the frontend.

---

## 4. Backend Validation

### New ErrorCode entries — `ErrorCode.kt`

```kotlin
// In the grid domain section:
GRID_GLOBAL_CLUE_LABEL_MISSING("Global clue label is required when word lengths are specified", 400),
GRID_GLOBAL_CLUE_NO_WORDS("Global clue must have at least one word", 400),
GRID_GLOBAL_CLUE_WORD_LENGTH_INVALID("Global clue word length must be at least 1", 400),
GRID_GLOBAL_CLUE_LETTER_COUNT_MISMATCH("Global clue letter count does not match indexed cell count", 400),
GRID_GLOBAL_CLUE_NUMBERING_INVALID("Indexed cell numbers must form a consecutive sequence starting at 1", 400),
```

### New GridError subtypes — `GridError.kt`

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

### Validation engine — `GridValidationEngine.kt`

Add a `validateGlobalClue()` method that operates directly on concrete DTO fields (outside the `GridDto` interface, since `globalClueWordLengths` is not part of the interface):

```kotlin
fun validateGlobalClue(
    globalClueLabel: String?,
    globalClueWordLengths: List<Int>?,
    cells: List<CellDto>?,
) {
    // If neither field is set, nothing to validate
    if (globalClueLabel == null && globalClueWordLengths == null) return

    // Label required when word lengths are provided
    if (globalClueWordLengths != null && globalClueLabel.isNullOrBlank()) {
        errors += GridError.GlobalClueLabelMissing
    }

    if (globalClueWordLengths != null) {
        // At least one word
        if (globalClueWordLengths.isEmpty()) {
            errors += GridError.GlobalClueNoWords
            return
        }

        // Each word length >= 1
        globalClueWordLengths.forEachIndexed { i, len ->
            if (len < 1) errors += GridError.GlobalClueWordLengthInvalid(i)
        }

        // Cell-level checks only when cells are present in the DTO
        if (cells != null) {
            val numberedCells = cells
                .filter { it.type == CellType.LETTER && it.number != null }
                .map { it.number!! }

            // Count mismatch
            val expected = globalClueWordLengths.sum()
            if (numberedCells.size != expected) {
                errors += GridError.GlobalClueLetterCountMismatch(expected, numberedCells.size)
            } else {
                // Numbering must be a gap-free sequence 1..N
                val sorted = numberedCells.sorted()
                val valid = sorted == (1..numberedCells.size).toList()
                if (!valid) errors += GridError.GlobalClueNumberingInvalid
            }
        }
        // If cells == null (partial patch without cells), cell-level checks are skipped.
        // The backend will have previously validated those cells on submit.
    }
}
```

### Calling the method

In `ValidateSubmitGridSchema.kt`:
```kotlin
engine.validateGlobalClue(dto.globalClueLabel, dto.globalClueWordLengths, dto.cells)
```

In `ValidatePatchGridSchema.kt`:
```kotlin
engine.validateGlobalClue(dto.globalClueLabel, dto.globalClueWordLengths, dto.cells)
```

Both calls pass `cells` directly from the DTO — it will be `null` on partial patches, causing cell-level checks to be skipped automatically.

---

## 5. Out of Scope

- Gameplay micro-grid above the game board (future — Phase 5+)
- Auto-numbering of cells by the system
- Enforcing maximum word length on the frontend
- GET grid API response shape (no GET endpoint exists yet for grids)
