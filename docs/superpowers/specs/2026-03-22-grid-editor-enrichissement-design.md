# Spec — Enrichissement éditeur de grille

**Date :** 2026-03-22
**Scope :** Angular 20 (frontend) + Kotlin/Spring Boot BFF

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

### 1.2 BFF Kotlin

#### `GridDifficulty` enum — suppression

Supprimer l'enum `GridDifficulty`. La difficulté est stockée comme `String` dans `GridMetadataDocument` et validée par pattern :

```kotlin
@Pattern(regexp = "^(NONE|0|1|2|3|4|5|0-1|1-2|2-3|3-4|4-5)$")
val difficulty: String = "NONE"
```

#### `GridMetadataDocument` — nouveaux champs

```kotlin
data class GridMetadataDocument(
    val author: String,
    val reference: String? = null,
    val description: String? = null,
    val difficulty: String = "NONE",
    val globalClueLabel: String? = null,        // nouveau
    val globalClueWords: List<List<Int>>? = null // nouveau
)
```

#### Validation (request DTO)

```kotlin
@Size(max = 200) val globalClueLabel: String? = null
@Size(max = 10)  val globalClueWords: List<@Size(max = 20) List<Int>>? = null
```

#### Mapper — nouveaux champs à propager

`CreateGridRequest → GridDocument` et `GridDocument → GridDto` : mapper `globalClueLabel` et `globalClueWords`.

---

## 2. Angular — DTO

### `grid.dto.ts` — `SubmitGridRequest`

Ajouter :
```typescript
globalClueLabel?: string;
globalClueWords?: number[][];
```

`cellToDto` — aucun changement (`number` déjà extrait de `letter.number`).

---

## 3. Composants Angular

### 3.1 `letter-input.component` — badge numéro

Ajouter dans le template un badge numéro en coin haut-droite, visible uniquement si `letter?.number` est défini :

```html
@if (letter?.number) {
  <span class="letter-input__number">{{ letter.number }}</span>
}
```

Style :
```scss
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

La cellule a déjà `position: relative` via `.cell` dans le wrapper parent.

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
```

Ajout d'un bouton "×" pour effacer le numéro (appelle `clearNumber()`).

### 3.3 `grid-params.component` — sélecteur de difficulté

Ajouter un `<select>` bindé à `grid().difficulty` via `GridSelectorService.updateDifficulty()`.

Valeurs :
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
- Prévisualisation mini-grille en lecture seule sous chaque mot : cases montrant la lettre de la cellule numérotée correspondante (ou `_` si vide/non trouvée)

**Logique :**
```typescript
// Parsing de l'input texte → number[]
parseWord(raw: string): number[] {
  return raw.split(',').map(s => parseInt(s.trim(), 10)).filter(n => !isNaN(n) && n > 0);
}

// Lookup lettre dans la grille pour la prévisualisation
getLetterForNumber(n: number): string {
  return this.selectorService.grid().cells
    .find(c => c.letter?.number === n)?.letter?.value ?? '_';
}
```

Injecte `GridSelectorService` et met à jour `grid.globalClue` via une méthode `updateGlobalClue()` à ajouter dans le service.

**Intégration :** ajouté dans `grid-editor.component.html` dans la `.editor-side` (sous les cards existantes), encapsulé dans `<cocro-card title="Énigme globale">`.

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

La mini-grille de l'indice global est **lecture seule** et **non navigable** (pas de `GridSelectorService.selectCell`).

Chaque case est un simple `<span>` stylé comme une cellule lettre miniature (taille réduite ~24px, même bordure, même police `--font-grid`), montrant la lettre ou `_`.

Les mots sont séparés par un espace visuel. Le label précède les mini-grilles.

---

## 6. Validation & limites

| Champ | Limite |
|---|---|
| `globalClue.label` | max 200 chars |
| `globalClue.words` | max 10 mots |
| `globalClue.words[i]` | max 20 numéros |
| `letter.number` | 1–99 |
| `difficulty` | valeur dans la liste des 12 valeurs autorisées |

---

## 7. Fichiers impactés

### Angular
- `domain/models/grid.model.ts` — types `GridDifficulty`, `GlobalClue`, champ `Grid.globalClue`
- `application/dto/grid.dto.ts` — `SubmitGridRequest` + `globalClue*`
- `application/service/grid-selector.service.ts` — `updateDifficulty`, `updateGlobalClue`
- `presentation/shared/grid/inputs/letter/letter-input.component.html/.scss` — badge numéro
- `presentation/features/grid/editor/letter-editor/letter-editor.component.ts/.html` — binding numéro
- `presentation/features/grid/editor/grid-params/grid-params.component.ts/.html/.scss` — sélecteur difficulté
- `presentation/features/grid/editor/global-clue-editor/` — nouveau composant (ts/html/scss)
- `presentation/features/grid/editor/grid-editor/grid-editor.component.html/.ts` — import + intégration
- `presentation/shared/grid/inputs/clues/clue-wrapper/clue-input.component.scss` — uppercase

### BFF
- `domain/` ou `application/` — suppression de `GridDifficulty` enum si elle existe
- `infrastructure/persistence/mongo/grid/document/GridMetadataDocument.kt` — nouveaux champs
- `presentation/dto/` — `CreateGridRequest`, `GridDto` : nouveaux champs + validation
- `application/mapper/GridMapper.kt` — mapping des nouveaux champs

---

## 8. Hors scope

- Navigation sur la mini-grille de l'indice global
- Réorganisation visuelle majeure de l'éditeur
- Tout changement sur le modèle de `Session` ou de `GameBoard`
