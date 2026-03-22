# Spec — Enrichissement éditeur de grille

**Date :** 2026-03-22
**Scope :** Angular 20 (frontend) + Kotlin/Spring Boot BFF + cocro-shared

---

## Contexte

Le projet CoCro est un éditeur collaboratif de mots fléchés. Cette spec couvre cinq enrichissements de l'éditeur de grille :

1. Numéro d'index sur les cases lettre (déjà dans le modèle, pas encore bindé ni rendu)
2. Indice global de la grille (énigme méta, nouveau champ)
3. Remplacement de l'échelle de difficulté EASY/MEDIUM/HARD
4. Affichage des indices en majuscules (CSS uniquement)
5. Sélecteur de difficulté dans les paramètres de la grille

Les séparateurs de mots (LEFT/UP) sont déjà fonctionnels — **hors scope**.

---

## 1. Modèle de données

### 1.1 Angular — `grid.model.ts`

#### `GridDifficulty` — remplacement complet

Supprimer `'EASY' | 'MEDIUM' | 'HARD' | 'NONE'`, remplacer par :

```typescript
export type GridDifficulty =
  | 'NONE'
  | '0' | '1' | '2' | '3' | '4' | '5'
  | '0-1' | '1-2' | '2-3' | '3-4' | '4-5';
```

Valeurs affichées dans le sélecteur : `–, 0, 1, 2, 3, 4, 5, 0-1, 1-2, 2-3, 3-4, 4-5`
(`NONE` → label `–`).

#### `GlobalClue` — nouveau type

```typescript
export interface GlobalClue {
  label: string;      // ex. "Auteur de l'Étranger :"  (max 200 chars)
  words: number[][];  // ex. [[1,2,3,4,5,6], [7,8,9,10,11]]
                      // max 10 mots, max 20 numéros par mot
}
```

#### `Grid` — nouveau champ

```typescript
export interface Grid {
  // ... champs existants ...
  difficulty?: GridDifficulty;  // type remplacé
  globalClue?: GlobalClue;      // nouveau
}
```

`Letter.number?: number` est déjà présent — aucun changement de modèle pour ce champ.

---

### 1.2 cocro-shared (Kotlin)

**`GridDifficulty.kt` — suppression de l'enum**

Le fichier `cocro-shared/src/commonMain/kotlin/com/cocro/kernel/grid/enums/GridDifficulty.kt` est supprimé. La difficulté devient une `String` libre validée côté BFF.

**`GridMetadata.kt` — remplacement du type**

```kotlin
// avant
data class GridMetadata(val difficulty: GridDifficulty, ...)

// après
data class GridMetadata(val difficulty: String = "NONE", ...)
```

---

### 1.3 BFF Kotlin

#### `GridMetadataDocument` — mise à jour + nouveaux champs

```kotlin
data class GridMetadataDocument(
    val author: String,
    val reference: String? = null,
    val description: String? = null,
    val difficulty: String = "NONE",        // String, plus GridDifficulty enum
    val globalClueLabel: String? = null,    // nouveau
    val globalClueWords: List<List<Int>>? = null // nouveau
)
```

#### `GridDto` / DTOs de requête — mise à jour

Dans `SubmitGridDto`, `PatchGridDto` et `GridDto` : remplacer `difficulty: GridDifficulty?` par `difficulty: String?`.

Validation sur le DTO de requête :
```kotlin
@Pattern(regexp = "^(NONE|0|1|2|3|4|5|0-1|1-2|2-3|3-4|4-5)$")
val difficulty: String? = "NONE"

@Size(max = 200) val globalClueLabel: String? = null
@Size(max = 10)  val globalClueWords: List<@Size(max = 20) List<Int>>? = null
```

#### Migration des données existantes (MongoDB)

Les grilles existantes stockent `difficulty` avec les valeurs `"EASY"`, `"MEDIUM"`, `"HARD"`. Le mapper BFF applique un **fallback sur lecture** :

```kotlin
fun mapDifficulty(raw: String?): String =
    when (raw) {
        "EASY"   -> "1-2"
        "MEDIUM" -> "2-3"
        "HARD"   -> "3-4"
        else     -> raw?.takeIf { it.matches(Regex("NONE|[0-5]|[0-4]-[1-5]")) } ?: "NONE"
    }
```

Ce mapping est appliqué dans `GridMapper` à la lecture (`GridDocument → GridDto`). Les documents MongoDB ne sont **pas migrés** — la transformation est transparente à chaque lecture. Les nouvelles écritures utilisent toujours la nouvelle échelle.

#### `GridMapper` — nouveaux champs

`CreateGridRequest → GridDocument` et `GridDocument → GridDto` : propager `globalClueLabel`, `globalClueWords`, et appliquer `mapDifficulty` sur la lecture.

---

## 2. Angular — DTO

### `grid.dto.ts` — `SubmitGridRequest`

Ajouter :
```typescript
globalClueLabel?: string;
globalClueWords?: number[][];
```

### `grid-editor.component.ts` — `onSubmit()`

La méthode `onSubmit()` construit manuellement `SubmitGridRequest`. Ajouter les champs :

```typescript
globalClueLabel: grid.globalClue?.label,
globalClueWords: grid.globalClue?.words,
```

`cellToDto` — aucun changement (`number` déjà extrait de `letter.number`).

---

## 3. Composants Angular

### 3.1 `letter-input.component` — badge numéro

Le composant `letter-input` contient actuellement un seul `<input>`. Ajouter un conteneur avec `position: relative` et le badge :

**`letter-input.component.html` :**
```html
<div class="letter-input-wrapper">
  <input type="text" ... />
  @if (letter?.number !== undefined && letter?.number !== null) {
    <span class="letter-input__number">{{ letter.number }}</span>
  }
</div>
```

**`letter-input.component.scss` :**
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

> **Note :** La garde `!== undefined && !== null` est explicite pour éviter qu'un `number = 0` (valeur falsy) soit invisiblement ignoré.

### 3.2 `letter-editor.component` — binding numéro

L'input numéro existe en HTML mais n'est pas bindé. Ajouter :

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

Template :
```html
<input type="number" min="1" max="99"
       [ngModel]="cell.letter?.number"
       (ngModelChange)="onNumberChange($event)" />
<button type="button" (click)="clearNumber()">×</button>
```

### 3.3 `grid-params.component` — sélecteur de difficulté

Ajouter un `<select>` bindé à `grid().difficulty` via `GridSelectorService.updateDifficulty()`.

```typescript
readonly DIFFICULTIES = [
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
```

Méthode `updateDifficulty(value: GridDifficulty)` à ajouter dans `GridSelectorService`.

### 3.4 `GlobalClueEditorComponent` — nouveau composant

**Chemin :** `presentation/features/grid/editor/global-clue-editor/`

**Responsabilité :** éditer `grid.globalClue` (label + mots).

**Template :**
- Input texte pour le `label` (placeholder : *"ex. Auteur de l'Étranger :"*)
- Liste dynamique de mots :
  - Chaque mot = un input texte de numéros séparés par virgules (ex. `"1, 2, 3, 4, 5, 6"`)
  - Bouton "×" pour supprimer un mot
  - Bouton "+ Ajouter un mot"
- Prévisualisation mini-grille en lecture seule sous chaque mot

**Logique — lookup via `computed()` signal :**

```typescript
// Map number → lettre, recalculée à chaque changement de grille
readonly letterByNumber = computed(() => {
  const map = new Map<number, string>();
  for (const cell of this.selectorService.grid().cells) {
    if (cell.letter?.number !== undefined) {
      map.set(cell.letter.number, cell.letter.value || '_');
    }
  }
  return map;
});

getLetterForNumber(n: number): string {
  return this.letterByNumber().get(n) ?? '_';
}
```

> **Performance :** `letterByNumber` est un `computed()` signal — recalculé uniquement quand `grid()` change, pas à chaque cycle de détection de changements.

**Références manquantes dans la prévisualisation :**

Si un numéro dans `globalClue.words` n'existe pas dans la grille, la case correspondante affiche `_` avec une couleur d'avertissement (`var(--color-red-light)`), signalant à l'éditeur qu'un numéro est non résolu.

**Parsing de l'input texte :**
```typescript
parseWord(raw: string): number[] {
  return raw.split(',')
    .map(s => parseInt(s.trim(), 10))
    .filter(n => !isNaN(n) && n > 0);
}
```

**Intégration :** ajouté dans `grid-editor.component.html` dans la `.editor-side`, encapsulé dans `<cocro-card title="Énigme globale">`.

### 3.5 `clue-input.component.scss` — majuscules

Ajouter dans `.text` :
```scss
text-transform: uppercase;
```

---

## 4. `GridSelectorService` — nouvelles méthodes

```typescript
updateDifficulty(difficulty: GridDifficulty): void
updateGlobalClue(globalClue: GlobalClue | undefined): void
```

Les deux muent le signal `grid` existant (pattern identique aux méthodes `updateTitle`, `initGrid`, etc.).

---

## 5. Rendu de la prévisualisation (mini-grille)

La mini-grille est **lecture seule** et **non navigable**.

Chaque case est un `<span>` stylé comme une mini-cellule lettre (~24px, même bordure, police `--font-grid`), montrant la lettre ou `_`. Les cases avec numéro non trouvé s'affichent en rouge clair (`var(--color-red-light)`).

Les mots sont séparés par un espace visuel. Le label précède les mini-grilles.

---

## 6. Validation & limites

| Champ | Limite |
|---|---|
| `globalClue.label` | max 200 chars |
| `globalClue.words` | max 10 mots |
| `globalClue.words[i]` | max 20 numéros |
| `letter.number` | 1–99, garde explicite `!== undefined && !== null` |
| `difficulty` | valeur dans la liste des 12 valeurs autorisées |

---

## 7. Fichiers impactés

### Angular
- `domain/models/grid.model.ts` — types `GridDifficulty`, `GlobalClue`, champ `Grid.globalClue`
- `application/dto/grid.dto.ts` — `SubmitGridRequest` + `globalClue*`
- `application/service/grid-selector.service.ts` — `updateDifficulty`, `updateGlobalClue`
- `presentation/shared/grid/inputs/letter/letter-input.component.html/.scss` — wrapper + badge numéro
- `presentation/features/grid/editor/letter-editor/letter-editor.component.ts/.html` — binding numéro + clearNumber
- `presentation/features/grid/editor/grid-params/grid-params.component.ts/.html/.scss` — sélecteur difficulté
- `presentation/features/grid/editor/global-clue-editor/` — nouveau composant (ts/html/scss)
- `presentation/features/grid/editor/grid-editor/grid-editor.component.html/.ts` — import, intégration, `onSubmit()` mis à jour
- `presentation/shared/grid/inputs/clues/clue-wrapper/clue-input.component.scss` — uppercase

### cocro-shared
- `cocro-shared/src/commonMain/kotlin/com/cocro/kernel/grid/enums/GridDifficulty.kt` — **suppression**
- `cocro-shared/src/commonMain/kotlin/com/cocro/kernel/grid/model/GridMetadata.kt` — `difficulty: String`

### BFF
- `application/grid/dto/GridDto.kt` — `difficulty: String?` (tous les DTOs grid)
- `infrastructure/persistence/mongo/grid/document/GridMetadataDocument.kt` — nouveaux champs
- `application/mapper/GridMapper.kt` — `mapDifficulty()` + mapping `globalClue*`

---

## 8. Hors scope

- Navigation sur la mini-grille de l'indice global
- Migration MongoDB des documents existants (remplacée par un fallback transparent à la lecture)
- Réorganisation visuelle majeure de l'éditeur
- Tout changement sur le modèle de `Session` ou de `GameBoard`
