# Plan d'implementation technique — Session Game Board enrichi

> Date : 2026-03-23
> PRD associe : `docs/superpowers/plans/2026-03-23-game-board-enrichissement-PRD.md`

---

## 1. File Map complet

### BFF — cocro-bff

| # | Chemin | Action | Description |
|---|--------|--------|-------------|
| B1 | `cocro-bff/src/main/kotlin/com/cocro/application/session/dto/GridTemplateDto.kt` | **CREATE** | DTO de reponse pour la structure de grille (sans lettres). Contient width, height, title, metadata, cells avec type/separator/number/clues mais letter vide. |
| B2 | `cocro-bff/src/main/kotlin/com/cocro/application/session/mapper/GridTemplateMapper.kt` | **CREATE** | Fonctions de mapping `Grid -> GridTemplateDto` : vide les `letter.value` de chaque `LetterCell`, preserve separator/number/clues/type. |
| B3 | `cocro-bff/src/main/kotlin/com/cocro/application/session/usecase/GetSessionGridTemplateUseCase.kt` | **CREATE** | Use case : authentifie l'utilisateur, charge la session par shareCode, verifie la participation, charge la grille via `GridRepository.findByShortId(session.gridId)`, mappe vers `GridTemplateDto`. |
| B4 | `cocro-bff/src/main/kotlin/com/cocro/presentation/rest/session/SessionController.kt` | **MODIFY** | Ajouter le endpoint `GET /api/sessions/{shareCode}/grid-template` + injection du `GetSessionGridTemplateUseCase`. |
| B5 | `cocro-bff/src/test/kotlin/com/cocro/application/session/usecase/GetSessionGridTemplateUseCaseTest.kt` | **CREATE** | Tests unitaires : unauthorized, session not found, grid not found, succes avec verification que les lettres sont videes et que separator/number/clues sont preserves. |

### Angular — cocro-web

| # | Chemin | Action | Description |
|---|--------|--------|-------------|
| A1 | `cocro-web/src/app/presentation/shared/grid/grid-wrapper/grid.component.ts` | **MODIFY** | Ajouter `@Input() disableKeyboard = false`. Dans `handleKey()`, early return si `disableKeyboard === true`. |
| A2 | `cocro-web/src/app/application/service/grid-selector.service.ts` | **MODIFY** | Ajouter `setLetterAt(x, y, letter)` et `clearLetterAt(x, y)` : mettent a jour la grille sans deplacer le curseur. |
| A3 | `cocro-web/src/app/domain/models/grid-template.model.ts` | **CREATE** | Interface `GridTemplateResponse` alignee sur le DTO BFF (reponse JSON brute). |
| A4 | `cocro-web/src/app/application/ports/session/session-grid-template.port.ts` | **CREATE** | Port `SessionGridTemplatePort` avec `getGridTemplate(shareCode: string): Observable<GridTemplateResponse>` + `InjectionToken SESSION_GRID_TEMPLATE_PORT`. |
| A5 | `cocro-web/src/app/infrastructure/adapters/session/session-grid-template-http.adapter.ts` | **CREATE** | Adapter HTTP implementant `SessionGridTemplatePort` : `GET /api/sessions/{shareCode}/grid-template`. |
| A6 | `cocro-web/src/app/infrastructure/adapters/session/grid-template.mapper.ts` | **CREATE** | Fonction `mapGridTemplateToGrid(dto: GridTemplateResponse): Grid` : convertit la reponse BFF en modele Angular `Grid` avec `letter.value = ''` pour toutes les `LetterCell`. |
| A7 | `cocro-web/src/app/presentation/features/game/board/game-board.component.ts` | **MODIFY** | Refactoring majeur : injection de `GridSelectorService` et `SESSION_GRID_TEMPLATE_PORT`, chargement grid-template + overlay state, keyboard handler propre, signal `letterAuthors`, gestion loading/error. |
| A8 | `cocro-web/src/app/presentation/features/game/board/game-board.component.html` | **MODIFY** | Remplacement du rendu custom par `<cocro-grid [disableKeyboard]="true" />`, ajout section metadonnees (titre, auteur, difficulte, description, reference, enigme globale), indicateur participants. |
| A9 | `cocro-web/src/app/presentation/features/game/board/game-board.component.scss` | **MODIFY** | Styles pour la section metadonnees, ajustement layout autour de `cocro-grid`, conservation des styles de selection/lettres via CSS classes au niveau host. |
| A10 | `cocro-web/src/app/presentation/shared/grid/inputs/letter/letter-input.component.ts` | **MODIFY** | Ajouter `@Input() colorClass: string = ''` pour permettre la differenciation visuelle me/other. |
| A11 | `cocro-web/src/app/presentation/shared/grid/inputs/letter/letter-input.component.html` | **MODIFY** | Appliquer `[class]="colorClass"` sur l'element affichant la lettre. |
| A12 | `cocro-web/src/app/presentation/shared/grid/inputs/letter/letter-input.component.scss` | **MODIFY** | Ajouter classes `.letter--mine` (couleur encre/vert foret) et `.letter--other` (brun/sepia). |
| A13 | `cocro-web/src/app/presentation/shared/grid/cell-wrapper/grid-cell.component.ts` | **MODIFY** | Ajouter un `@Input() letterColorClass: string = ''` passe au `cocro-letter-input`. |
| A14 | `cocro-web/src/app/presentation/shared/grid/cell-wrapper/grid-cell.component.html` | **MODIFY** | Passer `[colorClass]="letterColorClass"` a `<cocro-letter-input>`. |
| A15 | `cocro-web/src/app/presentation/shared/grid/grid-wrapper/grid.component.html` | **MODIFY** | Passer `[letterColorClass]` a chaque `<cocro-grid-cell>`, calcule via un callback/signal optionnel. |
| A16 | `cocro-web/src/app/presentation/shared/grid/grid-wrapper/grid.component.ts` | **MODIFY** | (en plus de A1) Ajouter `@Input() cellColorClassFn: ((x: number, y: number) => string) | null = null` pour la delegation de la colorisation. |

### Provisioning Angular (DI)

| # | Chemin | Action | Description |
|---|--------|--------|-------------|
| A17 | `cocro-web/src/app/presentation/features/game/game.routes.ts` | **MODIFY** | Ajouter le provider `{ provide: SESSION_GRID_TEMPLATE_PORT, useClass: SessionGridTemplateHttpAdapter }` dans les routes du module game. |

---

## 2. Steps d'implementation ordonnes

### Phase 1 : BFF — Endpoint grid-template

- [ ] **STEP 1 : Creer `GridTemplateDto`**
  - Fichier : `B1` — `cocro-bff/.../application/session/dto/GridTemplateDto.kt`
  - Notes :
    ```kotlin
    data class GridTemplateDto(
        val title: String,
        val width: Int,
        val height: Int,
        val difficulty: String,
        val author: String,       // UserId.toString()
        val reference: String?,
        val description: String?,
        val globalClueLabel: String?,
        val globalClueWordLengths: List<Int>?,
        val cells: List<GridTemplateCellDto>,
    )

    data class GridTemplateCellDto(
        val x: Int,
        val y: Int,
        val type: String,          // "LETTER" | "CLUE_SINGLE" | "CLUE_DOUBLE" | "BLACK"
        val separator: String?,    // "LEFT" | "UP" | "BOTH" | "NONE" — seulement pour LETTER
        val number: Int?,          // seulement pour LETTER (enigme globale)
        val clues: List<GridTemplateClueDto>?,
    )

    data class GridTemplateClueDto(
        val direction: String,     // "RIGHT" | "DOWN" | "FROM_BELOW" | "FROM_SIDE"
        val text: String,
    )
    ```
  - Pas de champ `letter` dans `GridTemplateCellDto` : c'est le but meme du template (la valeur de la lettre est effacee).

- [ ] **STEP 2 : Creer `GridTemplateMapper`**
  - Fichier : `B2` — `cocro-bff/.../application/session/mapper/GridTemplateMapper.kt`
  - Mapping `Grid -> GridTemplateDto` :
    - `Grid.title.value` -> `title`
    - `Grid.width.value` -> `width`, `Grid.height.value` -> `height`
    - `Grid.metadata.*` -> champs metadata
    - Pour chaque `Cell` :
      - `LetterCell` -> `GridTemplateCellDto(type="LETTER", separator=letter.separator.name, number=letter.number, clues=null)`
      - `SingleClueCell` -> `GridTemplateCellDto(type="CLUE_SINGLE", clues=[clue], ...)`
      - `DoubleClueCell` -> `GridTemplateCellDto(type="CLUE_DOUBLE", clues=[first, second], ...)`
      - `BlackCell` -> `GridTemplateCellDto(type="BLACK", ...)`
    - **Aucune lettre value n'est transmise** : le champ `letter` n'existe pas dans le DTO.
  - Dependances : `B1`

- [ ] **STEP 3 : Creer `GetSessionGridTemplateUseCase`**
  - Fichier : `B3` — `cocro-bff/.../application/session/usecase/GetSessionGridTemplateUseCase.kt`
  - Logique :
    1. `currentUserProvider.currentUserOrNull()` -> sinon `SessionError.Unauthorized`
    2. Parse `shareCode` en `SessionShareCode` -> sinon `SessionError.InvalidShareCode`
    3. `sessionRepository.findByShareCode(shareCode)` -> sinon `SessionError.SessionNotFound`
    4. Verifier que l'utilisateur est participant JOINED -> sinon `SessionError.UserNotParticipant`
    5. `gridRepository.findByShortId(session.gridId)` -> sinon `SessionError.ReferenceGridNotFound`
    6. `toGridTemplateDto(grid)` -> `CocroResult.Success(dto)`
  - Dependances : `B1`, `B2`
  - Injection : `CurrentUserProvider`, `SessionRepository`, `GridRepository`
  - Annotation : `@Service`

- [ ] **STEP 4 : Ajouter endpoint dans `SessionController`**
  - Fichier : `B4` — `cocro-bff/.../presentation/rest/session/SessionController.kt`
  - Ajouter au constructeur : `private val getSessionGridTemplateUseCase: GetSessionGridTemplateUseCase`
  - Ajouter import correspondant
  - Ajouter methode :
    ```kotlin
    @GetMapping("/{shareCode}/grid-template")
    @PreAuthorize("hasAnyRole('PLAYER', 'ADMIN', 'ANONYMOUS')")
    fun getSessionGridTemplate(
        @PathVariable shareCode: String,
    ): ResponseEntity<*> =
        getSessionGridTemplateUseCase
            .execute(shareCode)
            .toResponseEntity(HttpStatus.OK)
    ```
  - Dependances : `B3`

- [ ] **STEP 5 : Tests unitaires BFF**
  - Fichier : `B5` — `cocro-bff/.../test/.../GetSessionGridTemplateUseCaseTest.kt`
  - Pattern : copier le style de `GetSessionStateUseCaseTest` (mocks Mockito, AssertJ)
  - Cas a couvrir :
    1. `should return Unauthorized when user is not authenticated`
    2. `should return InvalidShareCode when shareCode format is invalid`
    3. `should return SessionNotFound when session does not exist`
    4. `should return UserNotParticipant when user is not a joined participant`
    5. `should return ReferenceGridNotFound when grid does not exist`
    6. `should return grid template with letters stripped` — le cas nominal :
       - Construire une `Grid` avec des `LetterCell(letter=Letter(LetterValue('A'), SeparatorType.LEFT, number=1))`, des `SingleClueCell`, `DoubleClueCell`, `BlackCell`
       - Verifier que le DTO resultant a `type="LETTER"` avec `separator="LEFT"` et `number=1` mais pas de `letter`
       - Verifier que les clues sont preservees avec text et direction
  - Dependances : `B1`, `B2`, `B3`

### Phase 2 : Angular — GridComponent et GridSelectorService

- [ ] **STEP 6 : Ajouter `disableKeyboard` a `GridComponent`**
  - Fichier : `A1` — `cocro-web/.../grid-wrapper/grid.component.ts`
  - Ajouter : `@Input() disableKeyboard = false;`
  - Modifier `handleKey()` : ajouter `if (this.disableKeyboard) return;` en premiere ligne.
  - Impact : zero regression sur l'editeur (qui ne passe pas le flag).

- [ ] **STEP 7 : Ajouter `cellColorClassFn` a `GridComponent`**
  - Fichier : `A16` — `cocro-web/.../grid-wrapper/grid.component.ts`
  - Ajouter : `@Input() cellColorClassFn: ((x: number, y: number) => string) | null = null;`
  - Fichier : `A15` — `cocro-web/.../grid-wrapper/grid.component.html`
  - Passer a chaque `<cocro-grid-cell>` : `[letterColorClass]="cellColorClassFn ? cellColorClassFn(cell.x, cell.y) : ''"`

- [ ] **STEP 8 : Propager `letterColorClass` dans `GridCellComponent`**
  - Fichier : `A13` — `cocro-web/.../cell-wrapper/grid-cell.component.ts`
  - Ajouter : `@Input() letterColorClass = '';`
  - Fichier : `A14` — `cocro-web/.../cell-wrapper/grid-cell.component.html`
  - Modifier : `<cocro-letter-input [letter]="cell.letter" [active]="selected()" [colorClass]="letterColorClass"/>`

- [ ] **STEP 9 : Ajouter `colorClass` a `LetterInputComponent`**
  - Fichier : `A10` — `cocro-web/.../inputs/letter/letter-input.component.ts`
  - Ajouter : `@Input() colorClass = '';`
  - Fichier : `A11` — `cocro-web/.../inputs/letter/letter-input.component.html`
  - Modifier l'input : ajouter `[class]="colorClass"` sur l'element `<input>` ou sur le wrapper.
  - Fichier : `A12` — `cocro-web/.../inputs/letter/letter-input.component.scss`
  - Ajouter :
    ```scss
    .letter--mine {
      color: var(--color-forest);
      font-weight: 700;
    }
    .letter--other {
      color: var(--color-sepia, #8B6914);
      font-weight: 600;
    }
    ```

- [ ] **STEP 10 : Ajouter `setLetterAt` et `clearLetterAt` a `GridSelectorService`**
  - Fichier : `A2` — `cocro-web/.../service/grid-selector.service.ts`
  - Ajouter :
    ```typescript
    setLetterAt(x: number, y: number, letter: string): void {
      const cell = getCell(this.grid(), x, y);
      if (!cell || !isCellLetter(cell)) return;
      const updated = writeLetterInCell(cell, letter);
      this.grid.update(g => withUpdatedCell(g, updated));
    }

    clearLetterAt(x: number, y: number): void {
      this.setLetterAt(x, y, '');
    }
    ```
  - **Difference cle avec `inputLetter`** : pas d'appel a `goToNextCell()`, le curseur ne bouge pas.

### Phase 3 : Angular — Port, Adapter, Mapper pour grid-template

- [ ] **STEP 11 : Creer `GridTemplateResponse` (modele)**
  - Fichier : `A3` — `cocro-web/.../domain/models/grid-template.model.ts`
  - Interface :
    ```typescript
    export interface GridTemplateResponse {
      title: string;
      width: number;
      height: number;
      difficulty: string;
      author: string;
      reference?: string;
      description?: string;
      globalClueLabel?: string;
      globalClueWordLengths?: number[];
      cells: GridTemplateCellDto[];
    }

    export interface GridTemplateCellDto {
      x: number;
      y: number;
      type: 'LETTER' | 'CLUE_SINGLE' | 'CLUE_DOUBLE' | 'BLACK';
      separator?: 'LEFT' | 'UP' | 'BOTH' | 'NONE';
      number?: number;
      clues?: GridTemplateClueDto[];
    }

    export interface GridTemplateClueDto {
      direction: 'RIGHT' | 'DOWN' | 'FROM_BELOW' | 'FROM_SIDE';
      text: string;
    }
    ```

- [ ] **STEP 12 : Creer le port `SessionGridTemplatePort`**
  - Fichier : `A4` — `cocro-web/.../application/ports/session/session-grid-template.port.ts`
  - ```typescript
    export interface SessionGridTemplatePort {
      getGridTemplate(shareCode: string): Observable<GridTemplateResponse>;
    }
    export const SESSION_GRID_TEMPLATE_PORT =
      new InjectionToken<SessionGridTemplatePort>('SESSION_GRID_TEMPLATE_PORT');
    ```

- [ ] **STEP 13 : Creer l'adapter HTTP**
  - Fichier : `A5` — `cocro-web/.../infrastructure/adapters/session/session-grid-template-http.adapter.ts`
  - ```typescript
    @Injectable({ providedIn: 'root' })
    export class SessionGridTemplateHttpAdapter implements SessionGridTemplatePort {
      private readonly baseUrl = `${environment.apiBaseUrl}/api/sessions`;
      constructor(private http: HttpClient) {}
      getGridTemplate(shareCode: string): Observable<GridTemplateResponse> {
        return this.http.get<GridTemplateResponse>(`${this.baseUrl}/${shareCode}/grid-template`);
      }
    }
    ```

- [ ] **STEP 14 : Creer le mapper `GridTemplateResponse -> Grid`**
  - Fichier : `A6` — `cocro-web/.../infrastructure/adapters/session/grid-template.mapper.ts`
  - Logique :
    ```typescript
    export function mapGridTemplateToGrid(dto: GridTemplateResponse): Grid {
      const cells: Cell[] = dto.cells.map(c => {
        const base = { x: c.x, y: c.y, type: c.type as CellType };
        switch (c.type) {
          case 'LETTER':
            return {
              ...base,
              letter: { value: '', separator: (c.separator ?? 'NONE') as SeparatorType, number: c.number },
            };
          case 'CLUE_SINGLE':
          case 'CLUE_DOUBLE':
            return {
              ...base,
              clues: c.clues?.map(cl => ({ direction: cl.direction as ClueDirection, text: cl.text })),
            };
          case 'BLACK':
            return base;
          default:
            return base;
        }
      });
      return {
        id: '',  // pas d'ID cote client pour un template
        title: dto.title,
        reference: dto.reference,
        width: dto.width,
        height: dto.height,
        cells,
        author: dto.author,
        difficulty: (dto.difficulty ?? 'NONE') as GridDifficulty,
        description: dto.description,
        globalClue: dto.globalClueLabel
          ? { label: dto.globalClueLabel, wordLengths: dto.globalClueWordLengths ?? [] }
          : undefined,
      };
    }
    ```

- [ ] **STEP 15 : Configurer le provider dans les routes game**
  - Fichier : `A17` — `cocro-web/.../features/game/game.routes.ts`
  - Ajouter le provider pour `SESSION_GRID_TEMPLATE_PORT` -> `SessionGridTemplateHttpAdapter`

### Phase 4 : Angular — Refactoring du GameBoardComponent

- [ ] **STEP 16 : Refactorer `GameBoardComponent` (logique TS)**
  - Fichier : `A7` — `cocro-web/.../game/board/game-board.component.ts`
  - **Changements majeurs :**

  **Imports supplementaires :**
  - `GridSelectorService`, `GridComponent`
  - `SESSION_GRID_TEMPLATE_PORT`, `SessionGridTemplatePort`
  - `mapGridTemplateToGrid`
  - `isCellLetter` de `@domain/services/cell-utils.service`

  **Nouveaux signaux :**
  ```typescript
  loading = signal(true);
  gridLoaded = signal(false);
  letterAuthors = signal(new Map<string, 'me' | string>());
  ```

  **Supprimer :**
  - `cellMap`, `rows`, `gridWidth`, `gridHeight` (remplaces par `GridSelectorService`)
  - `selectedX`, `selectedY` (remplaces par `GridSelectorService.selectedX/selectedY`)
  - `selectCell`, `isSelected` (geres par `GridComponent` + `GridSelectorService`)

  **Nouvelle injection :**
  ```typescript
  private selector = inject(GridSelectorService);
  private gridTemplatePort = inject(SESSION_GRID_TEMPLATE_PORT);
  ```

  **Nouveau `ngOnInit` :**
  1. Connecter le WebSocket (comme avant)
  2. Dans le callback `onEvent` du welcome :
     - `this.gridTemplatePort.getGridTemplate(shareCode).subscribe(template => { ... })`
     - `const grid = mapGridTemplateToGrid(template);`
     - `this.selector.initGrid(grid);`
     - `this.gridLoaded.set(true);`
     - Puis charger l'etat actuel : `this.gameSession.getState(shareCode).subscribe(state => { ... })`
     - Pour chaque cell du state : `this.selector.setLetterAt(c.x, c.y, c.letter)` + `letterAuthors.update(...)`
     - `this.loading.set(false);`

  **Nouveau keyboard handler :**
  ```typescript
  @HostListener('window:keydown', ['$event'])
  onKeyDown(event: KeyboardEvent): void {
    if (!this.gridLoaded() || this.loading()) return;

    const target = event.target as HTMLElement;
    const tag = target.tagName.toLowerCase();
    if (tag === 'input' || tag === 'textarea' || target.isContentEditable) return;
    if (event.ctrlKey || event.metaKey || event.altKey) return;

    const x = this.selector.selectedX();
    const y = this.selector.selectedY();

    switch (event.key) {
      case 'ArrowRight': this.selector.moveRight(); break;
      case 'ArrowLeft':  this.selector.moveLeft(); break;
      case 'ArrowDown':  this.selector.moveDown(); break;
      case 'ArrowUp':    this.selector.moveUp(); break;
      case 'Shift':
        // En mode jeu : inverse la direction (pas de transformation en case noire)
        this.selector.handleShift();
        break;
      case 'Backspace':
      case 'Delete':
        this.selector.clearLetterAt(x, y);
        this.sessionSocket.sendGridUpdate(this.shareCode(), {
          posX: x, posY: y, commandType: 'CLEAR_CELL',
        });
        this.letterAuthors.update(m => { const n = new Map(m); n.delete(`${x},${y}`); return n; });
        event.preventDefault();
        break;
      default:
        if (/^[a-zA-Z]$/.test(event.key)) {
          const letter = event.key.toUpperCase();
          this.selector.inputLetter(letter);  // ecrit + avance le curseur
          this.sessionSocket.sendGridUpdate(this.shareCode(), {
            posX: x, posY: y, commandType: 'PLACE_LETTER', letter,
          });
          this.letterAuthors.update(m => { const n = new Map(m); n.set(`${x},${y}`, 'me'); return n; });
          event.preventDefault();
        }
    }
  }
  ```

  **Handler `onGridUpdated` revise :**
  ```typescript
  private onGridUpdated(event: GridUpdatedEvent): void {
    if (event.actorId === this.myUserId()) return;
    this.revision.set(this.revision() + 1);
    if (event.commandType === 'PLACE_LETTER' && event.letter) {
      this.selector.setLetterAt(event.posX, event.posY, event.letter);
      this.letterAuthors.update(m => {
        const n = new Map(m);
        n.set(`${event.posX},${event.posY}`, event.actorId);
        return n;
      });
    } else if (event.commandType === 'CLEAR_CELL') {
      this.selector.clearLetterAt(event.posX, event.posY);
      this.letterAuthors.update(m => {
        const n = new Map(m);
        n.delete(`${event.posX},${event.posY}`);
        return n;
      });
    }
  }
  ```

  **Callback `cellColorClassFn` :**
  ```typescript
  getCellColorClass = (x: number, y: number): string => {
    const key = `${x},${y}`;
    const author = this.letterAuthors().get(key);
    if (!author) return '';
    return author === 'me' ? 'letter--mine' : 'letter--other';
  };
  ```

  **`ngOnDestroy` :**
  ```typescript
  ngOnDestroy(): void {
    this.sessionSocket.disconnect();
    // Reset grid selector pour eviter pollution si on revient a l'editeur
    this.selector.initGrid(createEmptyGrid('0', '', 10, 10));
  }
  ```

- [ ] **STEP 17 : Refactorer `game-board.component.html`**
  - Fichier : `A8` — `cocro-web/.../game/board/game-board.component.html`
  - Structure cible :
    ```html
    <div class="game" tabindex="0" aria-label="Grille de mots fleches">
      <!-- Navbar (identique, avec metadonnees enrichies) -->
      <nav class="game__nav">
        <span class="text-display game__brand">CoCro</span>
        <div class="game__nav-info">
          <span class="game__code text-mono">{{ shareCode() }}</span>
          <span class="section-label">
            {{ participantCount() }} joueur{{ participantCount() > 1 ? 's' : '' }}
          </span>
          <span class="game__revision text-muted">rev.{{ revision() }}</span>
          @if (!connected()) {
            <span class="game__offline">hors ligne</span>
          }
        </div>
        <cocro-button variant="ghost" (click)="leave()">Quitter</cocro-button>
      </nav>
      <hr class="divider" />

      <!-- Metadonnees de la grille -->
      @if (gridLoaded()) {
        <section class="game__metadata">
          <h2 class="game__title">{{ selector.grid().title }}</h2>
          @if (selector.grid().author) {
            <span class="game__author text-muted">par {{ selector.grid().author }}</span>
          }
          @if (selector.grid().difficulty && selector.grid().difficulty !== 'NONE') {
            <span class="game__difficulty">Difficulte : {{ selector.grid().difficulty }}</span>
          }
          @if (selector.grid().description) {
            <p class="game__description">{{ selector.grid().description }}</p>
          }
          @if (selector.grid().reference) {
            <span class="game__reference text-muted">Ref. {{ selector.grid().reference }}</span>
          }
          @if (selector.grid().globalClue) {
            <div class="game__global-clue">
              <span class="game__global-clue-label">{{ selector.grid().globalClue!.label }}</span>
              <span class="game__global-clue-lengths">
                ({{ selector.grid().globalClue!.wordLengths.join(', ') }})
              </span>
            </div>
          }
        </section>
      }

      <!-- Board -->
      <main class="game__board">
        @if (loading()) {
          <div class="game__loading">Chargement de la grille...</div>
        } @else {
          <cocro-grid
            [disableKeyboard]="true"
            [cellColorClassFn]="getCellColorClass" />
        }
      </main>
    </div>
    ```
  - **Pas de panneaux d'edition** (cell-type, clue-editor, letter-editor, grid-params) : ils ne sont pas importes.

- [ ] **STEP 18 : Styles SCSS du GameBoard**
  - Fichier : `A9` — `cocro-web/.../game/board/game-board.component.scss`
  - Ajouter :
    ```scss
    .game__metadata {
      display: flex;
      flex-wrap: wrap;
      align-items: baseline;
      gap: var(--space-md);
      padding: var(--space-md) var(--space-xl);
    }
    .game__title {
      font-family: var(--font-display);
      font-size: var(--text-xl);
      color: var(--color-ink);
      margin: 0;
    }
    .game__author, .game__reference {
      font-size: var(--text-sm);
    }
    .game__difficulty {
      font-family: var(--font-ui);
      font-size: var(--text-sm);
      background: var(--color-surface-alt);
      padding: 2px 8px;
      border-radius: var(--radius-sm);
    }
    .game__description {
      width: 100%;
      font-size: var(--text-base);
      font-style: italic;
      color: var(--color-ink-soft);
      margin: 0;
    }
    .game__global-clue {
      width: 100%;
      font-family: var(--font-display);
      font-size: var(--text-lg);
      color: var(--color-forest);
    }
    .game__loading {
      font-family: var(--font-display);
      font-size: var(--text-lg);
      color: var(--color-ink-soft);
      animation: fade-in 300ms ease-in-out;
    }
    ```
  - Supprimer les styles `game__cell`, `game__row`, `game__grid`, `game__letter` (remplaces par `cocro-grid`).

---

## 3. Contrats d'API

### `GET /api/sessions/{shareCode}/grid-template`

**Request :**
```http
GET /api/sessions/AB12/grid-template
Authorization: Bearer <jwt>
```

**Response 200 OK :**
```json
{
  "title": "Grille du dimanche",
  "width": 3,
  "height": 3,
  "difficulty": "2",
  "author": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "reference": "GDS-2026-12",
  "description": "Une grille thematique sur la nature",
  "globalClueLabel": "Animal cache",
  "globalClueWordLengths": [3, 5],
  "cells": [
    {
      "x": 0, "y": 0,
      "type": "CLUE_DOUBLE",
      "separator": null,
      "number": null,
      "clues": [
        { "direction": "RIGHT", "text": "Fruit tropical" },
        { "direction": "DOWN", "text": "Couleur du ciel" }
      ]
    },
    {
      "x": 1, "y": 0,
      "type": "LETTER",
      "separator": "NONE",
      "number": 1,
      "clues": null
    },
    {
      "x": 2, "y": 0,
      "type": "LETTER",
      "separator": "LEFT",
      "number": null,
      "clues": null
    },
    {
      "x": 0, "y": 1,
      "type": "LETTER",
      "separator": "NONE",
      "number": 2,
      "clues": null
    },
    {
      "x": 1, "y": 1,
      "type": "LETTER",
      "separator": "NONE",
      "number": null,
      "clues": null
    },
    {
      "x": 2, "y": 1,
      "type": "BLACK",
      "separator": null,
      "number": null,
      "clues": null
    },
    {
      "x": 0, "y": 2,
      "type": "LETTER",
      "separator": "UP",
      "number": null,
      "clues": null
    },
    {
      "x": 1, "y": 2,
      "type": "CLUE_SINGLE",
      "separator": null,
      "number": null,
      "clues": [
        { "direction": "FROM_SIDE", "text": "Oiseau noir" }
      ]
    },
    {
      "x": 2, "y": 2,
      "type": "LETTER",
      "separator": "NONE",
      "number": null,
      "clues": null
    }
  ]
}
```

**Reponses d'erreur :**
- `401 Unauthorized` : JWT absent/invalide
- `403 Forbidden` : utilisateur non participant de la session
- `404 Not Found` : session ou grille non trouvee

### Modele Angular `GridTemplateResponse`

```typescript
// cocro-web/src/app/domain/models/grid-template.model.ts

export interface GridTemplateResponse {
  title: string;
  width: number;
  height: number;
  difficulty: string;
  author: string;
  reference?: string;
  description?: string;
  globalClueLabel?: string;
  globalClueWordLengths?: number[];
  cells: GridTemplateCellDto[];
}

export interface GridTemplateCellDto {
  x: number;
  y: number;
  type: 'LETTER' | 'CLUE_SINGLE' | 'CLUE_DOUBLE' | 'BLACK';
  separator?: 'LEFT' | 'UP' | 'BOTH' | 'NONE' | null;
  number?: number | null;
  clues?: GridTemplateClueDto[] | null;
}

export interface GridTemplateClueDto {
  direction: 'RIGHT' | 'DOWN' | 'FROM_BELOW' | 'FROM_SIDE';
  text: string;
}
```

---

## 4. Points d'attention

### 4.1 Singleton `GridSelectorService` : navigation editeur <-> jeu

`GridSelectorService` est `providedIn: 'root'`, donc singleton global. Si l'utilisateur navigue :
- Editeur -> Jeu -> Editeur
- La grille de l'editeur serait ecrasee par `initGrid()` dans le GameBoard.

**Analyse de l'existant :** L'editeur appelle deja `initGrid()` dans son propre `ngOnInit` (via le `EditorComponent` qui charge le draft/grille). Donc le retour a l'editeur re-initialise proprement la grille.

**Action necessaire :**
- Dans `GameBoardComponent.ngOnDestroy()`, appeler `this.selector.initGrid(createEmptyGrid('0', '', 10, 10))` pour nettoyer l'etat.
- Verifier que le `EditorComponent` appelle bien `initGrid()` au chargement (c'est le cas : `onInit` charge le draft et appelle `initGrid`).

**Alternative future** : si ca pose probleme, envisager de fournir `GridSelectorService` au niveau du composant (`providers: [GridSelectorService]`) pour isoler les instances editeur et jeu. Mais pour le moment, le flux editeur -> jeu -> editeur est safe grace aux `initGrid()` symetriques.

### 4.2 `@HostListener('window:keydown')` dans `GridComponent`

Quand `disableKeyboard = true`, le listener est toujours attache au `window` mais fait un early return. Ce n'est pas ideal en termes de performance mais c'est negligeable (un seul check boolean par keydown). Si on voulait optimiser plus tard, on pourrait utiliser un `@HostListener` conditionnel via un Renderer2, mais c'est overkill pour le moment.

**Point critique :** `GameBoardComponent` a aussi un `@HostListener('window:keydown')`. Les deux listeners vont se declencher sur le meme evenement. L'ordre d'execution depend de l'arbre DOM Angular (parent avant enfant). Le `GridComponent` (enfant) fait un early return quand `disableKeyboard=true`, donc pas de double-execution de la logique.

### 4.3 Chargement async de la grille template

La sequence est :
1. WebSocket connect + welcome event
2. `GET /api/sessions/{shareCode}/grid-template` -> `initGrid(template)`
3. `GET /api/sessions/{shareCode}/state` -> `setLetterAt(...)` pour chaque cellule

Pendant les etapes 2 et 3, `loading()` est `true` et le composant affiche un placeholder. Le `<cocro-grid>` n'est rendu qu'une fois `loading() === false` (via le `@if` dans le template).

**Gestion d'erreur :** Ajouter un `catchError` dans les `subscribe` pour afficher un message d'erreur si le chargement du template echoue. Ajouter un signal `error = signal<string | null>(null)` pour cela.

### 4.4 `handleShift()` en mode jeu vs editeur

Dans l'editeur, `handleShift()` appelle `inverseDirection()` (inversion horizontal/vertical). C'est exactement le comportement voulu en mode jeu aussi. Le PRD mentionne "Shift inverse la direction (navigation) plutot que transformer en case noire", mais la transformation en case noire n'est pas dans `handleShift()` -- elle est dans `onCellTypeChange('BLACK')` (panneau d'edition). Donc aucun changement necessaire dans `handleShift()`.

### 4.5 `CellStateDto` vs `letterAuthors` pour la resync

Actuellement, `CellStateDto` retournee par `GET /state` ne contient pas l'`actorId`. Lors du `resync`, toutes les lettres sont chargees avec `actorId = ''` (inconnu). On ne peut pas distinguer "mes lettres" des "lettres des autres" apres un resync.

**Solution acceptable :** Apres un resync, les lettres apparaissent sans coloration speciale (ni mine, ni other). Seules les mises a jour en temps reel (via WS) beneficient de la coloration. C'est coherent avec l'experience utilisateur (on ne sait pas qui a pose quoi avant notre arrivee).

**Amelioration future :** Enrichir `CellStateDto` avec un `actorId` optionnel pour permettre la colorisation au resync. Hors perimetre de cette iteration.

### 4.6 `GridComponent` imports dans `GameBoardComponent`

`GameBoardComponent` doit ajouter `GridComponent` dans ses `imports` standalone. S'assurer que `GridComponent` est bien exporte (il l'est, c'est un composant standalone).

---

## 5. Plan de tests

### 5.1 Tests unitaires BFF

Fichier : `cocro-bff/src/test/kotlin/com/cocro/application/session/usecase/GetSessionGridTemplateUseCaseTest.kt`

| # | Test | Assertion cle |
|---|------|---------------|
| T1 | `should return Unauthorized when user is not authenticated` | `CocroResult.Error` contient `SessionError.Unauthorized`, `verifyNoInteractions(sessionRepository)` |
| T2 | `should return InvalidShareCode when shareCode format is invalid` | `CocroResult.Error` contient `SessionError.InvalidShareCode`, `verifyNoInteractions(sessionRepository)` |
| T3 | `should return SessionNotFound when session does not exist` | `CocroResult.Error` contient `SessionError.SessionNotFound`, `verifyNoInteractions(gridRepository)` |
| T4 | `should return UserNotParticipant when user is not a joined participant` | `CocroResult.Error` contient `SessionError.UserNotParticipant` |
| T5 | `should return ReferenceGridNotFound when grid does not exist` | `CocroResult.Error` contient `SessionError.ReferenceGridNotFound` |
| T6 | `should return grid template with letter values stripped` | `CocroResult.Success`, DTO a `cells.size == grid.cells.size`, aucune cellule LETTER n'a de champ `letter`, les `separator`, `number`, `clues` sont preserves |
| T7 | `should preserve all cell types in template` | Grille avec LETTER + CLUE_SINGLE + CLUE_DOUBLE + BLACK : verifier que chaque type est present dans le DTO |

### 5.2 Tests unitaires Angular

| # | Fichier | Description |
|---|---------|-------------|
| T8 | `grid-selector.service.spec.ts` | Ajouter tests pour `setLetterAt` et `clearLetterAt` : verifie que la grille est mise a jour sans changement de curseur |
| T9 | `grid-template.mapper.spec.ts` (CREATE) | Tester `mapGridTemplateToGrid` : tous les types de cellules, separator, number, clues |

### 5.3 Tests e2e (Playwright)

| # | Scenario | Description |
|---|----------|-------------|
| E1 | Chargement initial du GameBoard | Naviguer vers `/game/{shareCode}`, verifier que le spinner s'affiche puis disparait, verifier que la grille est rendue avec les bonnes dimensions et les clues visibles |
| E2 | Affichage des metadonnees | Verifier la presence du titre, auteur, difficulte, description dans le DOM |
| E3 | Saisie clavier | Cliquer sur une case LETTER, taper une lettre, verifier qu'elle apparait dans la case et que le curseur avance |
| E4 | Shift inverse la direction | Cliquer sur une case, verifier la direction initiale, appuyer sur Shift, verifier que la direction a change |
| E5 | Backspace efface la lettre | Saisir une lettre, appuyer sur Backspace, verifier que la case est vide |
| E6 | Reception mise a jour d'un autre joueur | Simuler un WebSocket event `GridUpdated` avec un `actorId` different, verifier que la lettre apparait avec la classe CSS `letter--other` |
| E7 | Deconnexion propre | Cliquer sur "Quitter", verifier la navigation vers `/`, verifier que le WebSocket est deconnecte |
| E8 | Cases noires et cases indices | Verifier que les cases BLACK sont rendues en noir et que les cases CLUE affichent les fleches et textes d'indice |

---

## 6. Diagramme de sequence (flux principal)

```
Client (Angular)                    BFF (Spring Boot)           MongoDB
      |                                    |                       |
      |---[WS CONNECT + Welcome]---------->|                       |
      |<--[SessionWelcome event]-----------|                       |
      |                                    |                       |
      |---GET /sessions/{sc}/grid-template>|                       |
      |                                    |---findByShareCode---->|
      |                                    |<--Session-------------|
      |                                    |---findByShortId------>|
      |                                    |<--Grid (full)---------|
      |                                    |                       |
      |                                    |  [strip letters]      |
      |<--200 GridTemplateDto -------------|                       |
      |                                    |                       |
      | [initGrid(template)]               |                       |
      |                                    |                       |
      |---GET /sessions/{sc}/state-------->|                       |
      |<--200 SessionStateDto -------------|                       |
      |                                    |                       |
      | [setLetterAt() for each cell]      |                       |
      | [loading = false]                  |                       |
      |                                    |                       |
      |  [user types 'A' on (2,3)]         |                       |
      |---[WS SEND PLACE_LETTER]---------->|                       |
      |                                    |  [apply command]      |
      |                                    |---[broadcast]-------->| (other clients)
```

---

## 7. Resume des decisions architecturales

| Decision | Choix | Raison |
|----------|-------|--------|
| API REST vs enrichissement WS | REST `GET /grid-template` | Cacheable, idempotent, plus simple que d'enrichir le welcome event |
| Keyboard handling | `@Input() disableKeyboard` sur `GridComponent` | Separation claire editeur/jeu, pas de refactoring lourd |
| Lettre posee sans deplacement curseur | `setLetterAt()` / `clearLetterAt()` dans `GridSelectorService` | Necessite pour les mises a jour WS d'autres joueurs |
| Differenciation visuelle me/other | Signal `letterAuthors` dans `GameBoardComponent` | Ne pollue pas le modele domaine `Grid` |
| Propagation couleur | `cellColorClassFn` callback sur `GridComponent` | Flexible, zero couplage entre GridComponent et le concept de "joueur" |
| Reset au `ngOnDestroy` | `initGrid(empty)` | Evite la pollution du singleton entre editeur et jeu |
