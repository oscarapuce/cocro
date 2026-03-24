# Grid Editor Enrichissement — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Enrichir l'éditeur de grille avec : badge numéro sur les cases lettre, indice global (GlobalClue), remplacement de la difficulté EASY/MEDIUM/HARD par une échelle 0–5, majuscules sur les indices, et sélecteur de difficulté dans les paramètres de la grille.

**Architecture:** Les changements traversent trois modules — cocro-shared (suppression de l'enum `GridDifficulty`), cocro-bff (DTOs, mapper, document MongoDB), et cocro-angular (modèles, service, composants). On part du bas (shared) vers le haut (UI) pour que chaque couche compile sur une base stable.

**Tech Stack:** Kotlin multiplatform (cocro-shared), Spring Boot 3.2 + Kotlin (cocro-bff), Angular 20 + Signals (cocro-angular), MongoDB, SCSS

---

## File Map

### cocro-shared
- Modify: `cocro-shared/src/commonMain/kotlin/com/cocro/kernel/grid/enums/GridDifficulty.kt` — **supprimer** ce fichier
- Modify: `cocro-shared/src/commonMain/kotlin/com/cocro/kernel/grid/model/GridMetadata.kt` — `difficulty: String = "NONE"` (non-nullable avec default)

### cocro-bff
- Modify: `cocro-bff/src/main/kotlin/com/cocro/application/grid/dto/GridDto.kt` — `difficulty: String` (toutes les occurrences)
- Modify: `cocro-bff/src/main/kotlin/com/cocro/infrastructure/persistence/mongo/grid/document/GridMetadataDocument.kt` — ajouter `globalClueLabel` et `globalClueWords`
- Modify: `cocro-bff/src/main/kotlin/com/cocro/infrastructure/persistence/mongo/grid/mapper/GridDocumentMapper.kt` — remplacer `GridDifficulty.valueOf()` / `.name` par `normalizeDifficulty()` + propager `globalClue*`
- Modify: `cocro-bff/src/main/kotlin/com/cocro/application/grid/mapper/GridMapper.kt` — propager `globalClueLabel`, `globalClueWords`, `difficulty: String`
- Modify: `cocro-shared/src/commonMain/kotlin/com/cocro/kernel/grid/model/GridMetadata.kt` (déjà listé)

### cocro-angular — domain / application
- Modify: `cocro-angular/src/app/domain/models/grid.model.ts` — nouveau type `GridDifficulty`, nouveau type `GlobalClue`, champ `Grid.globalClue?`
- Modify: `cocro-angular/src/app/application/dto/grid.dto.ts` — `SubmitGridRequest.difficulty: GridDifficulty`, ajouter `globalClueLabel?` et `globalClueWords?`
- Modify: `cocro-angular/src/app/application/service/grid-selector.service.ts` — ajouter `updateDifficulty()` et `updateGlobalClue()`

### cocro-angular — presentation
- Modify: `cocro-angular/src/app/presentation/shared/grid/inputs/letter/letter-input.component.html` — wrapper div + badge numéro
- Modify: `cocro-angular/src/app/presentation/shared/grid/inputs/letter/letter-input.component.scss` — `.letter-input-wrapper` + `.letter-input__number`
- Modify: `cocro-angular/src/app/presentation/features/grid/editor/letter-editor/letter-editor.component.ts` — `onNumberChange()` + `clearNumber()`
- Modify: `cocro-angular/src/app/presentation/features/grid/editor/letter-editor/letter-editor.component.html` — binding input numéro + bouton ×
- Modify: `cocro-angular/src/app/presentation/features/grid/editor/grid-params/grid-params.component.ts` — constante `DIFFICULTIES` + `updateDifficulty()`
- Modify: `cocro-angular/src/app/presentation/features/grid/editor/grid-params/grid-params.component.html` — `<select>` difficulté
- Create: `cocro-angular/src/app/presentation/features/grid/editor/global-clue-editor/global-clue-editor.component.ts`
- Create: `cocro-angular/src/app/presentation/features/grid/editor/global-clue-editor/global-clue-editor.component.html`
- Create: `cocro-angular/src/app/presentation/features/grid/editor/global-clue-editor/global-clue-editor.component.scss`
- Modify: `cocro-angular/src/app/presentation/shared/grid/inputs/clues/clue-wrapper/clue-input.component.scss` — `text-transform: uppercase`
- Modify: `cocro-angular/src/app/presentation/features/grid/editor/grid-editor/grid-editor.component.ts` — import `GlobalClueEditorComponent`
- Modify: `cocro-angular/src/app/presentation/features/grid/editor/grid-editor/grid-editor.component.html` — intégration `<cocro-global-clue-editor>`
- Modify: `cocro-angular/src/app/presentation/features/grid/editor/grid-editor/grid-editor.component.ts` — `onSubmit()` avec `globalClueLabel/Words`

---

## Task 1 — cocro-shared : supprimer GridDifficulty, mettre à jour GridMetadata

**Files:**
- Delete: `cocro-shared/src/commonMain/kotlin/com/cocro/kernel/grid/enums/GridDifficulty.kt`
- Modify: `cocro-shared/src/commonMain/kotlin/com/cocro/kernel/grid/model/GridMetadata.kt`

- [ ] **Step 1: Supprimer le fichier GridDifficulty.kt**

```bash
rm cocro-shared/src/commonMain/kotlin/com/cocro/kernel/grid/enums/GridDifficulty.kt
```

- [ ] **Step 2: Mettre à jour GridMetadata.kt**

Remplacer le contenu par :

```kotlin
package com.cocro.kernel.grid.model

import com.cocro.kernel.auth.model.valueobject.UserId

data class GridMetadata(
    val author: UserId,
    val reference: String?,
    val description: String?,
    val difficulty: String = "NONE",
)
```

- [ ] **Step 3: Mettre à jour le test `SessionGridStateCheckAgainstTest.kt` dans cocro-shared**

Ce test référence `GridDifficulty.NONE` — doit être fixé avant le build.

Fichier : `cocro-shared/src/jvmTest/kotlin/com/cocro/kernel/session/model/state/SessionGridStateCheckAgainstTest.kt`

Remplacer :
```kotlin
import com.cocro.kernel.grid.enums.GridDifficulty
```
Par : *(supprimer cette ligne)*

Et remplacer :
```kotlin
difficulty = GridDifficulty.NONE,
```
Par :
```kotlin
difficulty = "NONE",
```

- [ ] **Step 4: Builder le module shared pour vérifier qu'il compile**

```bash
./gradlew cocro-shared:build
```

Expected: BUILD SUCCESSFUL — pas d'erreur de compilation. Les tests existants qui référençaient `GridDifficulty` vont échouer à compiler dans cocro-bff ; c'est attendu, on les corrige à l'étape suivante.

- [ ] **Step 5: Commit**

```bash
git add cocro-shared/src/commonMain/kotlin/com/cocro/kernel/grid/enums/GridDifficulty.kt \
        cocro-shared/src/commonMain/kotlin/com/cocro/kernel/grid/model/GridMetadata.kt \
        cocro-shared/src/jvmTest/kotlin/com/cocro/kernel/session/model/state/SessionGridStateCheckAgainstTest.kt
git commit -m "feat(shared): replace GridDifficulty enum with String, default NONE"
```

---

## Task 2 — BFF : mettre à jour GridDto, GridDocumentMapper, GridMetadataDocument, GridMapper

**Files:**
- Modify: `cocro-bff/src/main/kotlin/com/cocro/application/grid/dto/GridDto.kt`
- Modify: `cocro-bff/src/main/kotlin/com/cocro/infrastructure/persistence/mongo/grid/document/GridMetadataDocument.kt`
- Modify: `cocro-bff/src/main/kotlin/com/cocro/infrastructure/persistence/mongo/grid/mapper/GridDocumentMapper.kt`
- Modify: `cocro-bff/src/main/kotlin/com/cocro/application/grid/mapper/GridMapper.kt`

- [ ] **Step 1: Mettre à jour GridMetadata.kt dans cocro-shared — ajouter globalClue* (doit précéder les mappers BFF)**

Les mappers BFF référencent `metadata.globalClueLabel` et `metadata.globalClueWords` — ces champs doivent exister dans `GridMetadata` avant de modifier les mappers.

```kotlin
package com.cocro.kernel.grid.model

import com.cocro.kernel.auth.model.valueobject.UserId

data class GridMetadata(
    val author: UserId,
    val reference: String?,
    val description: String?,
    val difficulty: String = "NONE",
    val globalClueLabel: String? = null,
    val globalClueWords: List<List<Int>>? = null,
)
```

- [ ] **Step 3: Mettre à jour GridDto.kt — remplacer `GridDifficulty` par `String`**

> **Note :** Le BFF utilise un DSL de validation custom (voir `ValidateSubmitGridSchema.kt`). Si la validation de la valeur `difficulty` est souhaitée, l'ajouter dans ce DSL — pas avec `@Pattern` (Jakarta Validation n'est pas dans les dépendances).

```kotlin
package com.cocro.application.grid.dto

import com.cocro.kernel.grid.enums.CellType
import com.cocro.kernel.grid.enums.ClueDirection
import com.cocro.kernel.grid.enums.SeparatorType

interface GridDto {
    val gridId: String?
    val title: String?
    val difficulty: String?
    val reference: String?
    val description: String?
    val width: Int?
    val height: Int?
    val cells: List<CellDto>?
}

data class SubmitGridDto(
    override val gridId: String? = null,
    override val title: String,
    override val difficulty: String = "NONE",
    override val reference: String?,
    override val description: String?,
    override val width: Int,
    override val height: Int,
    override val cells: List<CellDto>,
    val globalClueLabel: String? = null,
    val globalClueWords: List<List<Int>>? = null,
) : GridDto

data class PatchGridDto(
    override val gridId: String,
    override val title: String?,
    override val difficulty: String?,
    override val reference: String?,
    override val description: String?,
    override val width: Int?,
    override val height: Int?,
    override val cells: List<CellDto>?,
    val globalClueLabel: String? = null,
    val globalClueWords: List<List<Int>>? = null,
) : GridDto

data class ClueDto(
    val direction: ClueDirection,
    val text: String,
)

data class CellDto(
    val x: Int,
    val y: Int,
    val type: CellType,
    val letter: String?,
    val separator: SeparatorType?,
    val number: Int?,
    val clues: List<ClueDto>?,
)
```

- [ ] **Step 4: Mettre à jour GridMetadataDocument.kt — ajouter globalClue***

```kotlin
package com.cocro.infrastructure.persistence.mongo.grid.document

data class GridMetadataDocument(
    val author: String,
    val reference: String?,
    val description: String?,
    val difficulty: String = "NONE",
    val globalClueLabel: String? = null,
    val globalClueWords: List<List<Int>>? = null,
)
```

- [ ] **Step 5: Mettre à jour GridDocumentMapper.kt — remplacer .name / .valueOf(), propager globalClue***

Remplacer le contenu par :

```kotlin
package com.cocro.infrastructure.persistence.mongo.grid.mapper

import com.cocro.infrastructure.persistence.mongo.grid.document.CellDocument
import com.cocro.infrastructure.persistence.mongo.grid.document.GridDocument
import com.cocro.infrastructure.persistence.mongo.grid.document.GridMetadataDocument
import com.cocro.kernel.auth.model.valueobject.UserId
import com.cocro.kernel.grid.enums.CellType
import com.cocro.kernel.grid.enums.ClueDirection
import com.cocro.kernel.grid.enums.SeparatorType
import com.cocro.kernel.grid.model.Cell
import com.cocro.kernel.grid.model.CellPos
import com.cocro.kernel.grid.model.Clue
import com.cocro.kernel.grid.model.Grid
import com.cocro.kernel.grid.model.GridMetadata
import com.cocro.kernel.grid.model.Letter
import com.cocro.kernel.grid.model.valueobject.ClueText
import com.cocro.kernel.grid.model.valueobject.GridHeight
import com.cocro.kernel.grid.model.valueobject.GridShareCode
import com.cocro.kernel.grid.model.valueobject.GridTitle
import com.cocro.kernel.grid.model.valueobject.GridWidth
import com.cocro.kernel.grid.model.valueobject.LetterValue

private val VALID_DIFFICULTIES = setOf("NONE","0","1","2","3","4","5","0-1","1-2","2-3","3-4","4-5")

private fun normalizeDifficulty(raw: String?): String =
    if (raw != null && raw in VALID_DIFFICULTIES) raw else "NONE"

fun Grid.toDocument(): GridDocument =
    GridDocument(
        id = id,
        shortId = shortId.value,
        title = title.value,
        metadata = GridMetadataDocument(
            author = metadata.author.toString(),
            reference = metadata.reference,
            description = metadata.description,
            difficulty = metadata.difficulty,
            globalClueLabel = metadata.globalClueLabel,
            globalClueWords = metadata.globalClueWords,
        ),
        hashLetters = hashLetters,
        width = width.value,
        height = height.value,
        cells = cells.map { it.toDocument() },
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

fun Cell.toDocument(): CellDocument =
    when (this) {
        is Cell.LetterCell ->
            CellDocument(
                x = pos.x,
                y = pos.y,
                type = "LETTER",
                letter = letter.value.value,
                separator = letter.separator.name,
                number = letter.number,
            )
        is Cell.ClueCell.SingleClueCell ->
            CellDocument(
                x = pos.x,
                y = pos.y,
                type = "CLUE_SINGLE",
                clueDirection = clue.direction.name,
                clueText = clue.text.value,
            )
        is Cell.ClueCell.DoubleClueCell ->
            CellDocument(
                x = pos.x,
                y = pos.y,
                type = "CLUE_DOUBLE",
                clueDirection = first.direction.name,
                clueText = first.text.value,
                secondClueDirection = second.direction.name,
                secondClueText = second.text.value,
            )
        is Cell.BlackCell ->
            CellDocument(x = pos.x, y = pos.y, type = "BLACK")
    }

fun GridDocument.toDomain(): Grid =
    Grid(
        id = id,
        shortId = GridShareCode(shortId),
        title = GridTitle(title),
        metadata = GridMetadata(
            author = UserId.from(metadata.author),
            reference = metadata.reference,
            description = metadata.description,
            difficulty = normalizeDifficulty(metadata.difficulty),
            globalClueLabel = metadata.globalClueLabel,
            globalClueWords = metadata.globalClueWords,
        ),
        hashLetters = hashLetters,
        width = GridWidth(width),
        height = GridHeight(height),
        cells = cells.map { it.toDomain() },
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

fun CellDocument.toDomain(): Cell {
    val pos = CellPos(x, y)
    return when (type) {
        CellType.LETTER.name ->
            Cell.LetterCell(
                pos,
                Letter(
                    value = LetterValue(letter!!),
                    separator = SeparatorType.valueOf(separator!!),
                    number = number,
                ),
            )
        CellType.CLUE_SINGLE.name ->
            Cell.ClueCell.SingleClueCell(
                pos,
                Clue(direction = ClueDirection.valueOf(clueDirection!!), text = ClueText(clueText!!)),
            )
        CellType.CLUE_DOUBLE.name ->
            Cell.ClueCell.DoubleClueCell(
                pos,
                Clue(direction = ClueDirection.valueOf(clueDirection!!), text = ClueText(clueText!!)),
                Clue(direction = ClueDirection.valueOf(secondClueDirection!!), text = ClueText(secondClueText!!)),
            )
        CellType.BLACK.name -> Cell.BlackCell(pos)
        else -> error("Unknown cell type: $type")
    }
}
```

> **Note :** `GridMetadata` va avoir de nouveaux champs `globalClueLabel` et `globalClueWords` — on les ajoute en Task 2 Step 4 (GridMapper) simultanément.

- [ ] **Step 6: Mettre à jour GridMapper.kt — propager difficulty (String) + globalClue***

```kotlin
package com.cocro.application.grid.mapper

import com.cocro.application.grid.dto.CellDto
import com.cocro.application.grid.dto.ClueDto
import com.cocro.application.grid.dto.PatchGridDto
import com.cocro.application.grid.dto.SubmitGridDto
import com.cocro.kernel.auth.model.valueobject.UserId
import com.cocro.kernel.grid.enums.CellType
import com.cocro.kernel.grid.enums.SeparatorType
import com.cocro.kernel.grid.model.Cell
import com.cocro.kernel.grid.model.CellPos
import com.cocro.kernel.grid.model.Clue
import com.cocro.kernel.grid.model.Grid
import com.cocro.kernel.grid.model.GridMetadata
import com.cocro.kernel.grid.model.Letter
import com.cocro.kernel.grid.model.valueobject.ClueText
import com.cocro.kernel.grid.model.valueobject.GridHeight
import com.cocro.kernel.grid.model.valueobject.GridShareCode
import com.cocro.kernel.grid.model.valueobject.GridTitle
import com.cocro.kernel.grid.model.valueobject.GridWidth
import com.cocro.kernel.grid.model.valueobject.LetterValue
import java.util.UUID

internal fun SubmitGridDto.toDomain(
    shortId: GridShareCode,
    userId: UserId,
): Grid =
    Grid(
        id = UUID.randomUUID(),
        shortId = shortId,
        title = GridTitle(this.title),
        metadata = GridMetadata(
            difficulty = this.difficulty,
            author = userId,
            reference = this.reference,
            description = this.description,
            globalClueLabel = this.globalClueLabel,
            globalClueWords = this.globalClueWords,
        ),
        width = GridWidth(this.width),
        height = GridHeight(this.height),
        cells = this.cells.map { it.toDomain() },
    )

internal fun PatchGridDto.applyPatchTo(grid: Grid): Grid {
    val patchedMetadata = grid.metadata.copy(
        difficulty = this.difficulty ?: grid.metadata.difficulty,
        reference = this.reference ?: grid.metadata.reference,
        description = this.description ?: grid.metadata.description,
        globalClueLabel = if (this.globalClueLabel != null) this.globalClueLabel else grid.metadata.globalClueLabel,
        globalClueWords = if (this.globalClueWords != null) this.globalClueWords else grid.metadata.globalClueWords,
    )
    val patchedWidth = this.width?.let { GridWidth(it) } ?: grid.width
    val patchedHeight = this.height?.let { GridHeight(it) } ?: grid.height
    val patchedCells = this.cells?.map { it.toDomain() } ?: grid.cells

    return grid.copy(
        title = this.title?.let { GridTitle(it) } ?: grid.title,
        metadata = patchedMetadata,
        width = patchedWidth,
        height = patchedHeight,
        cells = patchedCells,
    )
}

private fun CellDto.toDomain(): Cell {
    val pos = CellPos(this.x, this.y)
    return when (this.type) {
        CellType.LETTER ->
            Cell.LetterCell(
                pos,
                Letter(
                    value = LetterValue(this.letter!![0]),
                    separator = this.separator ?: SeparatorType.NONE,
                    number = this.number,
                ),
            )
        CellType.CLUE_SINGLE ->
            Cell.ClueCell.SingleClueCell(pos, this.clues!![0].toDomain())
        CellType.CLUE_DOUBLE ->
            Cell.ClueCell.DoubleClueCell(
                pos,
                this.clues!![0].toDomain(),
                this.clues[1].toDomain(),
            )
        CellType.BLACK -> Cell.BlackCell(pos)
    }
}

private fun ClueDto.toDomain(): Clue =
    Clue(direction = this.direction, text = ClueText(this.text))
```

- [ ] **Step 7: Fixer les fichiers de tests BFF qui référençaient `GridDifficulty`**

Rechercher et remplacer dans les tests :

```bash
grep -rl "GridDifficulty" cocro-bff/src/test/
```

Pour chaque fichier trouvé, remplacer les occurrences de `GridDifficulty.EASY`, `GridDifficulty.NONE`, etc. par leur équivalent string : `"EASY"` → `"NONE"` (puisqu'on supprime l'enum, on utilise `"NONE"` comme valeur par défaut dans les tests).

Exemple de pattern à remplacer dans les DTOs de test :
```kotlin
// Avant
difficulty = GridDifficulty.NONE
// Après
difficulty = "NONE"
```

- [ ] **Step 8: Builder le BFF pour vérifier qu'il compile et que les tests passent**

```bash
./gradlew cocro-bff:build
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 9: Commit**

```bash
git add cocro-shared/src/commonMain/kotlin/com/cocro/kernel/grid/model/GridMetadata.kt \
        cocro-bff/src/main/kotlin/com/cocro/application/grid/dto/GridDto.kt \
        cocro-bff/src/main/kotlin/com/cocro/infrastructure/persistence/mongo/grid/document/GridMetadataDocument.kt \
        cocro-bff/src/main/kotlin/com/cocro/infrastructure/persistence/mongo/grid/mapper/GridDocumentMapper.kt \
        cocro-bff/src/main/kotlin/com/cocro/application/grid/mapper/GridMapper.kt
git commit -m "feat(bff): difficulty as String, add globalClue fields to grid metadata"
```

---

## Task 3 — Angular domain/application : modèles, DTO, GridSelectorService

**Files:**
- Modify: `cocro-angular/src/app/domain/models/grid.model.ts`
- Modify: `cocro-angular/src/app/application/dto/grid.dto.ts`
- Modify: `cocro-angular/src/app/application/service/grid-selector.service.ts`

- [ ] **Step 1: Mettre à jour grid.model.ts**

```typescript
export type CellType = 'LETTER' | 'CLUE_SINGLE' | 'CLUE_DOUBLE' | 'BLACK';
export type ClueDirection = 'RIGHT' | 'DOWN' | 'FROM_BELOW' | 'FROM_SIDE';
export type Direction = 'DOWNWARDS' | 'RIGHTWARDS' | 'NONE';
export type SeparatorType = 'LEFT' | 'UP' | 'BOTH' | 'NONE';
export type GridDifficulty =
  | 'NONE'
  | '0' | '1' | '2' | '3' | '4' | '5'
  | '0-1' | '1-2' | '2-3' | '3-4' | '4-5';

export interface GlobalClue {
  label: string;
  words: number[][];
}

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
  letter?: Letter;
  clues?: Clue[];
  type: CellType;
}

export interface Grid {
  id: string;
  title: string;
  width: number;
  height: number;
  cells: Cell[];
  author?: string;
  difficulty?: GridDifficulty;
  description?: string;
  globalClue?: GlobalClue;
}
```

- [ ] **Step 2: Mettre à jour grid.dto.ts — ajouter globalClue* dans SubmitGridRequest**

```typescript
import { Cell, CellType, ClueDirection, GridDifficulty, SeparatorType } from '@domain/models/grid.model';

export interface ClueDto {
  direction: ClueDirection;
  text: string;
}

export interface CellDto {
  x: number;
  y: number;
  type: CellType;
  letter?: string;
  separator?: SeparatorType;
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
  globalClueLabel?: string;
  globalClueWords?: number[][];
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

export function cellToDto(cell: Cell): CellDto {
  return {
    x: cell.x,
    y: cell.y,
    type: cell.type,
    letter: cell.letter?.value || undefined,
    separator: cell.letter?.separator,
    number: cell.letter?.number,
    clues: cell.clues?.length ? cell.clues : undefined,
  };
}
```

- [ ] **Step 3: Ajouter updateDifficulty() et updateGlobalClue() dans GridSelectorService**

Ajouter ces deux méthodes publiques à la fin de la classe, avant la dernière accolade :

```typescript
updateTitle(title: string): void {
  this.grid.update(g => ({ ...g, title }));
}

updateDifficulty(difficulty: GridDifficulty): void {
  this.grid.update(g => ({ ...g, difficulty }));
}

updateGlobalClue(globalClue: GlobalClue | undefined): void {
  this.grid.update(g => ({ ...g, globalClue }));
}
```

> **Note :** `updateTitle` existe peut-être déjà — vérifier avant d'ajouter. Seuls `updateDifficulty` et `updateGlobalClue` sont nouveaux.

Ajouter les imports manquants en haut du fichier :
```typescript
import { GlobalClue, GridDifficulty } from '@domain/models/grid.model';
```

- [ ] **Step 4: Build Angular pour vérifier qu'il compile**

```bash
cd cocro-angular && npx ng build 2>&1 | tail -20
```

Expected: 0 errors

- [ ] **Step 5: Commit**

```bash
git add cocro-angular/src/app/domain/models/grid.model.ts \
        cocro-angular/src/app/application/dto/grid.dto.ts \
        cocro-angular/src/app/application/service/grid-selector.service.ts
git commit -m "feat(angular): new GridDifficulty type, GlobalClue model, updateDifficulty/updateGlobalClue"
```

---

## Task 4 — Angular : badge numéro sur letter-input

**Files:**
- Modify: `cocro-angular/src/app/presentation/shared/grid/inputs/letter/letter-input.component.html`
- Modify: `cocro-angular/src/app/presentation/shared/grid/inputs/letter/letter-input.component.scss`

- [ ] **Step 1: Mettre à jour le template**

```html
<div class="letter-input-wrapper">
  <input
    type="text"
    maxlength="1"
    pattern="[A-Za-z]"
    class="letter-input"
    [value]="letter.value"
    [disabled]="!active"
    [readonly]="!active"
    (input)="onInput($event)"
    inputmode="latin" />
  @if (letter?.number !== undefined && letter?.number !== null) {
    <span class="letter-input__number">{{ letter.number }}</span>
  }
</div>
```

- [ ] **Step 2: Mettre à jour le SCSS**

```scss
.letter-input-wrapper {
  position: relative;
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.letter-input__number {
  position: absolute;
  top: 1px;
  right: 2px;
  font-size: 0.45rem;
  font-family: var(--font-ui);
  font-weight: 700;
  color: var(--color-ink-muted);
  line-height: 1;
  pointer-events: none;
}
```

Le reste du fichier SCSS existant (`.letter-input`) est conservé tel quel.

- [ ] **Step 3: Build Angular**

```bash
cd cocro-angular && npx ng build 2>&1 | tail -20
```

Expected: 0 errors

- [ ] **Step 4: Commit**

```bash
git add cocro-angular/src/app/presentation/shared/grid/inputs/letter/letter-input.component.html \
        cocro-angular/src/app/presentation/shared/grid/inputs/letter/letter-input.component.scss
git commit -m "feat(angular): display letter number badge in cell corner"
```

---

## Task 5 — Angular : binding numéro dans letter-editor

**Files:**
- Modify: `cocro-angular/src/app/presentation/features/grid/editor/letter-editor/letter-editor.component.ts`
- Modify: `cocro-angular/src/app/presentation/features/grid/editor/letter-editor/letter-editor.component.html`

- [ ] **Step 1: Ajouter onNumberChange() et clearNumber() dans le composant TS**

Ajouter dans `LetterEditorComponent` (après `toggleSep`) :

```typescript
onNumberChange(value: string): void {
  const n = parseInt(value, 10);
  this.ensureLetter();
  this.cell.letter!.number = isNaN(n) || n < 1 ? undefined : n;
}

clearNumber(): void {
  this.ensureLetter();
  this.cell.letter!.number = undefined;
}
```

- [ ] **Step 2: Mettre à jour le template HTML**

Remplacer l'`<input type="text" maxlength="2" placeholder="1">` non bindé par :

```html
<div class="letter-editor-card">
  <div class="letter-item">
    <div class="separator-type-toggle">
      <button
        class="toggle-btn"
        [class.active]="isSepActive('left')"
        (click)="toggleSep('left')"
      >Gauche</button>
      <button
        class="toggle-btn"
        [class.active]="isSepActive('up')"
        (click)="toggleSep('up')"
      >Haut</button>
    </div>

    <div class="number-field">
      <input type="number" min="1" max="99"
             [ngModel]="cell.letter?.number"
             (ngModelChange)="onNumberChange($event)" />
      <button type="button" class="clear-btn" (click)="clearNumber()">×</button>
    </div>
  </div>
</div>
```

- [ ] **Step 3: Build Angular**

```bash
cd cocro-angular && npx ng build 2>&1 | tail -20
```

Expected: 0 errors

- [ ] **Step 4: Commit**

```bash
git add cocro-angular/src/app/presentation/features/grid/editor/letter-editor/letter-editor.component.ts \
        cocro-angular/src/app/presentation/features/grid/editor/letter-editor/letter-editor.component.html
git commit -m "feat(angular): bind letter number field in letter-editor"
```

---

## Task 6 — Angular : sélecteur difficulté dans grid-params

**Files:**
- Modify: `cocro-angular/src/app/presentation/features/grid/editor/grid-params/grid-params.component.ts`
- Modify: `cocro-angular/src/app/presentation/features/grid/editor/grid-params/grid-params.component.html`

- [ ] **Step 1: Ajouter DIFFICULTIES et updateDifficulty dans le composant TS**

```typescript
import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { GridSelectorService } from '@application/service/grid-selector.service';
import { GridDifficulty } from '@domain/models/grid.model';
import { ButtonComponent } from '@presentation/shared/components/button/button.component';

@Component({
  selector: 'cocro-grid-params',
  standalone: true,
  imports: [FormsModule, ButtonComponent],
  templateUrl: './grid-params.component.html',
  styleUrls: ['./grid-params.component.scss'],
})
export class GridParamsComponent {
  readonly selectorService = inject(GridSelectorService);

  readonly DIFFICULTIES: { value: GridDifficulty; label: string }[] = [
    { value: 'NONE', label: '–' },
    { value: '0',   label: '0' },
    { value: '1',   label: '1' },
    { value: '2',   label: '2' },
    { value: '3',   label: '3' },
    { value: '4',   label: '4' },
    { value: '5',   label: '5' },
    { value: '0-1', label: '0-1' },
    { value: '1-2', label: '1-2' },
    { value: '2-3', label: '2-3' },
    { value: '3-4', label: '3-4' },
    { value: '4-5', label: '4-5' },
  ];

  updateDifficulty(value: string): void {
    this.selectorService.updateDifficulty(value as GridDifficulty);
  }

  onAddRow() {
    const grid = this.selectorService.grid();
    this.selectorService.onResize(grid.width, grid.height + 1);
  }

  onRemoveRow() {
    const grid = this.selectorService.grid();
    this.selectorService.onResize(grid.width, grid.height - 1);
  }

  onAddColumn() {
    const grid = this.selectorService.grid();
    this.selectorService.onResize(grid.width + 1, grid.height);
  }

  onRemoveColumn() {
    const grid = this.selectorService.grid();
    this.selectorService.onResize(grid.width - 1, grid.height);
  }
}
```

- [ ] **Step 2: Ajouter le select dans le template HTML**

Ajouter après le dernier `grid-params__size-control` (avant la fermeture de `.grid-params`) :

```html
  <div class="grid-params__separator"></div>

  <div class="grid-params__difficulty">
    <label for="grid-difficulty">Difficulté</label>
    <select
      id="grid-difficulty"
      [ngModel]="selectorService.grid().difficulty ?? 'NONE'"
      (ngModelChange)="updateDifficulty($event)"
    >
      @for (d of DIFFICULTIES; track d.value) {
        <option [value]="d.value">{{ d.label }}</option>
      }
    </select>
  </div>
```

- [ ] **Step 3: Build Angular**

```bash
cd cocro-angular && npx ng build 2>&1 | tail -20
```

Expected: 0 errors

- [ ] **Step 4: Commit**

```bash
git add cocro-angular/src/app/presentation/features/grid/editor/grid-params/grid-params.component.ts \
        cocro-angular/src/app/presentation/features/grid/editor/grid-params/grid-params.component.html
git commit -m "feat(angular): add difficulty selector to grid-params"
```

---

## Task 7 — Angular : indices en majuscules (CSS seulement)

**Files:**
- Modify: `cocro-angular/src/app/presentation/shared/grid/inputs/clues/clue-wrapper/clue-input.component.scss`

- [ ] **Step 1: Ajouter text-transform: uppercase dans .text**

Dans le bloc `.clue .text`, ajouter `text-transform: uppercase;` :

```scss
.text {
  padding: 0.05rem 0.05rem;
  white-space: pre-wrap;
  overflow: clip;
  text-overflow: ellipsis;
  display: inline-block;
  color: var(--color-ink);
  font-weight: 700;
  text-wrap: balance;
  text-transform: uppercase;
}
```

- [ ] **Step 2: Build Angular**

```bash
cd cocro-angular && npx ng build 2>&1 | tail -20
```

Expected: 0 errors

- [ ] **Step 3: Commit**

```bash
git add cocro-angular/src/app/presentation/shared/grid/inputs/clues/clue-wrapper/clue-input.component.scss
git commit -m "feat(angular): display clue text in uppercase"
```

---

## Task 8 — Angular : GlobalClueEditorComponent (nouveau composant)

**Files:**
- Create: `cocro-angular/src/app/presentation/features/grid/editor/global-clue-editor/global-clue-editor.component.ts`
- Create: `cocro-angular/src/app/presentation/features/grid/editor/global-clue-editor/global-clue-editor.component.html`
- Create: `cocro-angular/src/app/presentation/features/grid/editor/global-clue-editor/global-clue-editor.component.scss`

- [ ] **Step 1: Créer le composant TS**

```typescript
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

  // Map number → lettre, recalculée uniquement quand grid() change
  readonly letterByNumber = computed(() => {
    const map = new Map<number, string>();
    for (const cell of this.selectorService.grid().cells) {
      if (cell.letter?.number !== undefined && cell.letter?.number !== null) {
        map.set(cell.letter.number, cell.letter.value || '_');
      }
    }
    return map;
  });

  getLetterForNumber(n: number): string {
    return this.letterByNumber().get(n) ?? '_';
  }

  isUnresolved(n: number): boolean {
    return !this.letterByNumber().has(n);
  }

  parseWord(raw: string): number[] {
    return raw.split(',')
      .map(s => parseInt(s.trim(), 10))
      .filter(n => !isNaN(n) && n > 0);
  }

  wordToRaw(word: number[]): string {
    return word.join(', ');
  }

  get globalClue(): GlobalClue | undefined {
    return this.selectorService.grid().globalClue;
  }

  updateLabel(label: string): void {
    const current = this.globalClue;
    this.selectorService.updateGlobalClue({
      label,
      words: current?.words ?? [],
    });
  }

  updateWord(index: number, raw: string): void {
    const current = this.globalClue;
    if (!current) return;
    const words = [...current.words];
    words[index] = this.parseWord(raw);
    this.selectorService.updateGlobalClue({ ...current, words });
  }

  addWord(): void {
    const current = this.globalClue;
    const words = current ? [...current.words, []] : [[]];
    this.selectorService.updateGlobalClue({
      label: current?.label ?? '',
      words,
    });
  }

  removeWord(index: number): void {
    const current = this.globalClue;
    if (!current) return;
    const words = current.words.filter((_, i) => i !== index);
    this.selectorService.updateGlobalClue({ ...current, words });
  }

  clearGlobalClue(): void {
    this.selectorService.updateGlobalClue(undefined);
  }
}
```

- [ ] **Step 2: Créer le template HTML**

```html
<div class="global-clue-editor">
  <div class="global-clue-editor__label-field">
    <input
      type="text"
      maxlength="200"
      placeholder="ex. Auteur de l'Étranger :"
      [ngModel]="globalClue?.label ?? ''"
      (ngModelChange)="updateLabel($event)"
    />
  </div>

  @if (globalClue) {
    <div class="global-clue-editor__words">
      @for (word of globalClue.words; track $index) {
        <div class="global-clue-editor__word-row">
          <input
            type="text"
            placeholder="1, 2, 3"
            [ngModel]="wordToRaw(word)"
            (ngModelChange)="updateWord($index, $event)"
          />
          <button type="button" class="remove-btn" (click)="removeWord($index)">×</button>

          <div class="global-clue-editor__preview">
            @for (n of word; track $index) {
              <span
                class="preview-cell"
                [class.preview-cell--unresolved]="isUnresolved(n)"
              >{{ getLetterForNumber(n) }}</span>
            }
          </div>
        </div>
      }
    </div>

    <button type="button" class="add-word-btn" (click)="addWord()">+ Ajouter un mot</button>
  } @else {
    <button type="button" class="add-word-btn" (click)="addWord()">+ Ajouter un mot</button>
  }
</div>
```

- [ ] **Step 3: Créer le SCSS**

```scss
.global-clue-editor {
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
  padding: var(--space-sm) 0;

  &__label-field {
    input {
      width: 100%;
      padding: var(--space-xs) var(--space-sm);
      font-size: var(--text-sm);
      font-family: var(--font-ui);
      border: var(--border-dashed);
      background-color: var(--color-input-bg);
      outline: none;

      &:focus {
        border-color: var(--color-forest);
        background-color: var(--color-surface);
      }
    }
  }

  &__words {
    display: flex;
    flex-direction: column;
    gap: var(--space-xs);
  }

  &__word-row {
    display: flex;
    flex-direction: column;
    gap: var(--space-2xs);

    input {
      width: 100%;
      padding: var(--space-xs) var(--space-sm);
      font-size: var(--text-sm);
      font-family: var(--font-ui);
      border: var(--border-dashed);
      background-color: var(--color-input-bg);
      outline: none;

      &:focus {
        border-color: var(--color-forest);
        background-color: var(--color-surface);
      }
    }
  }

  &__preview {
    display: flex;
    flex-wrap: wrap;
    gap: 2px;
  }
}

.remove-btn {
  align-self: flex-end;
  background: none;
  border: none;
  color: var(--color-ink-muted);
  cursor: pointer;
  font-size: var(--text-base);
  padding: 0 var(--space-xs);

  &:hover {
    color: var(--color-ink);
  }
}

.add-word-btn {
  align-self: flex-start;
  background: none;
  border: 1px dashed var(--color-border);
  color: var(--color-forest);
  cursor: pointer;
  font-family: var(--font-ui);
  font-size: var(--text-sm);
  padding: var(--space-2xs) var(--space-sm);

  &:hover {
    background-color: var(--color-croco-wash);
  }
}

.preview-cell {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border: 1px solid var(--color-border);
  font-family: var(--font-grid);
  font-size: 0.7rem;
  font-weight: 700;
  text-transform: uppercase;
  color: var(--color-ink);
  background-color: var(--color-paper);

  &--unresolved {
    color: var(--color-red-light);
    border-color: var(--color-red-light);
  }
}
```

- [ ] **Step 4: Build Angular**

```bash
cd cocro-angular && npx ng build 2>&1 | tail -20
```

Expected: 0 errors

- [ ] **Step 5: Commit**

```bash
git add cocro-angular/src/app/presentation/features/grid/editor/global-clue-editor/
git commit -m "feat(angular): add GlobalClueEditorComponent with word preview"
```

---

## Task 9 — Angular : intégration dans grid-editor (onSubmit + template)

**Files:**
- Modify: `cocro-angular/src/app/presentation/features/grid/editor/grid-editor/grid-editor.component.ts`
- Modify: `cocro-angular/src/app/presentation/features/grid/editor/grid-editor/grid-editor.component.html`

- [ ] **Step 1: Mettre à jour grid-editor.component.ts — import et onSubmit()**

Ajouter en haut du fichier :
```typescript
import { GlobalClueEditorComponent } from '@presentation/features/grid/editor/global-clue-editor/global-clue-editor.component';
```

Dans le décorateur `@Component`, ajouter `GlobalClueEditorComponent` dans le tableau `imports: [...]` :
```typescript
imports: [
  GridComponent,
  CardComponent,
  ButtonComponent,
  ClueEditorComponent,
  LetterEditorComponent,
  GridParamsComponent,
  CellTypeComponent,
  GlobalClueEditorComponent,  // ← ajouter cette ligne
],
```

Mettre à jour `onSubmit()` pour inclure les champs globalClue :

```typescript
async onSubmit() {
  this.saving.set(true);
  try {
    const grid = this.selectorService.grid();
    const request: SubmitGridRequest = {
      gridId: grid.id,
      title: grid.title,
      difficulty: grid.difficulty ?? 'NONE',
      description: grid.description,
      width: grid.width,
      height: grid.height,
      cells: grid.cells.map(cellToDto),
      globalClueLabel: grid.globalClue?.label,
      globalClueWords: grid.globalClue?.words,
    };
    const id = await this.createGridUseCase.execute(request);
    this.toast.success(`Grille creee avec succes (ID: ${id})`);
  } catch (e: any) {
    const message = e.message ? e.message : 'Erreur inconnue';
    this.toast.error(message);
  } finally {
    this.saving.set(false);
  }
}
```

- [ ] **Step 2: Mettre à jour le template — ajouter GlobalClueEditor dans la side**

Ajouter `<cocro-global-clue-editor>` dans `.editor-side`, après les cards de type et d'édition cellule :

```html
<div class="grid-editor">
  <cocro-card class="grid-params-bar" title="Paramètres de la grille">
    <cocro-grid-params />
    <cocro-button variant="primary" size="sm" (click)="onSubmit()" [disabled]="saving()">
      Créer la grille
    </cocro-button>
  </cocro-card>

  <div class="grid-workspace">
    <aside class="editor-side">
      <cocro-cell-type />

      @if (isClueSelected()) {
        <cocro-card title="Contenu de l'indice">
          <cocro-clue-editor [cell]="selectorService.selectedCell()!" />
        </cocro-card>
      } @else if (isLetterSelected()) {
        <cocro-card title="Contenu de la lettre">
          <cocro-letter-editor [cell]="selectorService.selectedCell()!" />
        </cocro-card>
      }

      <cocro-card title="Énigme globale">
        <cocro-global-clue-editor />
      </cocro-card>
    </aside>

    <div class="grid-wrapper">
      <cocro-grid />
    </div>
  </div>
</div>
```

- [ ] **Step 3: Build Angular final**

```bash
cd cocro-angular && npx ng build 2>&1 | tail -20
```

Expected: 0 errors, 0 warnings (hors budget si SCSS grossit — vérifier et ajuster le budget dans `angular.json` si nécessaire)

- [ ] **Step 4: Commit final**

```bash
git add cocro-angular/src/app/presentation/features/grid/editor/grid-editor/grid-editor.component.ts \
        cocro-angular/src/app/presentation/features/grid/editor/grid-editor/grid-editor.component.html
git commit -m "feat(angular): integrate GlobalClueEditor and globalClue fields in grid-editor submit"
```

---

## Vérification finale

- [ ] Builder le projet complet

```bash
./gradlew build 2>&1 | tail -30
```

Expected: BUILD SUCCESSFUL pour tous les modules

- [ ] Lancer le serveur Angular en dev

```bash
cd cocro-angular && npx ng serve
```

Tester manuellement :
1. Ouvrir l'éditeur de grille
2. Sélectionner une case lettre → vérifier que le champ numéro est bindé
3. Saisir un numéro → vérifier le badge dans le coin de la case
4. Vérifier le sélecteur de difficulté dans la barre de paramètres
5. Vérifier que les indices s'affichent en majuscules
6. Vérifier le composant Énigme globale : saisir un label, ajouter des mots avec des numéros, vérifier la prévisualisation
7. Cliquer "Créer la grille" → vérifier que la requête inclut les bons champs
