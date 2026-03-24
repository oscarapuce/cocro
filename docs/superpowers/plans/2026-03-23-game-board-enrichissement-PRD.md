# PRD : Session Game Board enrichi

**Date** : 2026-03-23
**Auteur** : Product Owner / UX Lead
**Statut** : Draft
**Feature** : Afficher la structure complète de la grille dans le Game Board de session

---

## 1. Persona et contexte utilisateur

### 1.1 Personas

**Le Cruciverbiste Createur (CC)**
Utilisateur authentifie (role PLAYER ou ADMIN) qui a concu une grille dans l'editeur CoCro. Il a passe du temps a placer des cases indice, des cases noires, des separateurs, des numeros, et eventuellement une enigme globale. Il cree ensuite une session pour jouer sa grille avec d'autres. Il s'attend a retrouver **exactement** le rendu visuel de sa grille dans la vue de jeu.

**Le Joueur Invite (JI)**
Utilisateur (authentifie ou anonyme) qui rejoint une session via un code de partage. Il n'a jamais vu la grille dans l'editeur. Il decouvre la grille pour la premiere fois dans le Game Board. Il a besoin de voir les indices, les separateurs, les numeros et les cases noires pour comprendre la structure du mot fleche et pouvoir jouer.

### 1.2 Objectif des utilisateurs

Les deux personas veulent **jouer collaborativement** a la grille de mots fleches en temps reel. Pour cela, ils doivent :
- Voir la grille complete avec sa structure (indices, cases noires, separateurs, numeros)
- Comprendre quelles cases sont jouables (LETTER) et lesquelles sont de la structure (CLUE, BLACK)
- Saisir des lettres dans les cases LETTER et voir les saisies des autres joueurs apparaitre en temps reel
- Identifier visuellement qui a pose une lettre (moi vs. un autre joueur)

### 1.3 Etat mental

- **CC** : Fierte de voir sa grille jouee par d'autres, attente d'une experience visuelle coherente avec l'editeur
- **JI** : Curiosite et decouverte, besoin d'une interface intuitive et lisible sans explication prealable
- **Les deux** : Concentration sur le jeu, immersion dans l'univers "Atelier du Cruciverbiste" (papier, stylo, cahier)

---

## 2. User Stories (format Gherkin)

### US1 : Voir la grille avec sa structure complete en session

```gherkin
Scenario: Le joueur voit la structure complete de la grille en session
  Given un joueur connecte a une session dont la grille contient des cases LETTER, CLUE_SINGLE, CLUE_DOUBLE et BLACK
  When la session est en statut PLAYING et le Game Board s'affiche
  Then le joueur voit :
    | Element                        | Visible | Interactif |
    | Cases LETTER (vides)           | oui     | oui        |
    | Cases CLUE_SINGLE avec texte   | oui     | non        |
    | Cases CLUE_DOUBLE avec textes  | oui     | non        |
    | Cases BLACK                    | oui     | non        |
    | Separateurs (LEFT, UP, BOTH)   | oui     | non        |
    | Numeros dans les cases LETTER  | oui     | non        |
  And les cases LETTER ne contiennent pas les lettres-solution de la grille originale
  And le rendu visuel est identique a celui du composant <cocro-grid> de l'editeur
```

### US2 : Voir les metadonnees de la grille

```gherkin
Scenario: Le joueur voit les informations de la grille en jeu
  Given un joueur connecte a une session
  And la grille a un titre "Mots du printemps", une difficulte "2", un auteur, et une description "Grille thematique sur le printemps"
  When le Game Board s'affiche
  Then le joueur voit le titre "Mots du printemps" affiche en evidence
  And il voit l'indication de difficulte "2"
  And il voit le nom de l'auteur de la grille
  And il peut acceder a la description de la grille (tooltip ou section depliable)

Scenario: Le joueur voit la reference de la grille
  Given une grille avec une reference "Revue CoCro n.42"
  When le Game Board s'affiche
  Then la reference est affichee sous le titre

Scenario: Le joueur voit l'enigme globale
  Given une grille avec une enigme globale (globalClue) definie
  When le Game Board s'affiche
  Then l'enigme globale est affichee au-dessus de la grille
  And les cases numerotees alimentent la preview de l'enigme au fur et a mesure que les lettres sont posees
```

### US3 : Saisir des lettres et les voir se propager

```gherkin
Scenario: Le joueur saisit une lettre dans une case LETTER
  Given un joueur avec une case LETTER selectionnee en (3, 5)
  When il appuie sur la touche "A"
  Then la lettre "A" apparait dans la case (3, 5) avec le style "posee par moi"
  And la commande PLACE_LETTER est envoyee au serveur via WebSocket
  And le curseur avance automatiquement a la prochaine case LETTER dans la direction active

Scenario: Un autre joueur voit la lettre posee
  Given le joueur A a pose la lettre "A" en (3, 5)
  When l'evenement GridUpdated arrive chez le joueur B
  Then le joueur B voit "A" en (3, 5) avec le style "posee par un autre"

Scenario: Le joueur efface une lettre
  Given un joueur avec une case LETTER contenant "A" selectionnee
  When il appuie sur Backspace
  Then la case est videe
  And la commande CLEAR_CELL est envoyee au serveur
  And le curseur recule a la case LETTER precedente dans la direction active

Scenario: Le joueur navigue avec les fleches
  Given un joueur avec une case selectionnee
  When il appuie sur ArrowRight / ArrowLeft / ArrowUp / ArrowDown
  Then la selection se deplace dans la direction correspondante
  And la selection saute les cases non-LETTER (CLUE, BLACK) si le GridSelectorService le gere

Scenario: Le joueur ne peut pas modifier la structure
  Given un joueur en mode jeu
  When il appuie sur Shift
  Then la direction de saisie s'inverse (RIGHTWARDS <-> DOWNWARDS)
  And la cellule selectionnee ne se transforme PAS en case noire (contrairement a l'editeur)
```

### US4 : Rejoindre une session en cours et voir l'etat actuel

```gherkin
Scenario: Un joueur rejoint une session deja commencee
  Given une session en statut PLAYING avec des lettres deja posees par d'autres joueurs
  When un nouveau joueur se connecte via WebSocket et recoit SessionWelcome
  Then il voit la grille complete avec sa structure
  And toutes les lettres deja posees sont affichees dans les cases correspondantes
  And les lettres pre-existantes apparaissent avec le style "posee par un autre"

Scenario: Resynchronisation apres deconnexion
  Given un joueur temporairement deconnecte
  When il se reconnecte et recoit SyncRequired
  Then il appelle GET /api/sessions/{shareCode}/state
  And la grille est remise a jour avec l'etat courant (lettres posees)
  And la structure de la grille reste inchangee
```

---

## 3. Exigences fonctionnelles

### 3.1 Ce que le Game Board DOIT afficher

| Element | Source | Detail |
|---------|--------|--------|
| Grille structuree | BFF (via Welcome ou endpoint dedie) | Toutes les cellules avec leur type, indices, separateurs, numeros |
| Cases LETTER | Structure de la grille | Vides au depart (lettres-solution retirees), remplies au fur et a mesure |
| Cases CLUE (SINGLE et DOUBLE) | Structure de la grille | Texte des indices affiche, direction visuelle |
| Cases BLACK | Structure de la grille | Fond noir, non interactives |
| Separateurs | Structure de la grille (Letter.separator) | Trait epais a gauche (LEFT), en haut (UP), ou les deux (BOTH) |
| Numeros | Structure de la grille (Letter.number) | Petit chiffre en haut a gauche de la case |
| Enigme globale | Structure de la grille (GlobalClue) | Label + preview avec lettres numerotees, au-dessus de la grille |
| Titre de la grille | Metadonnees | En evidence dans l'en-tete du Game Board |
| Difficulte | Metadonnees | Indicateur visuel (etoiles, badge, etc.) |
| Auteur | Metadonnees | Sous le titre |
| Reference | Metadonnees (optionnel) | Sous le titre si presente |
| Description | Metadonnees (optionnel) | Accessible via tooltip ou section depliable |
| Nombre de participants | SessionWelcome + events | Mis a jour en temps reel |
| Code de session | Deja present | Pour permettre le partage oral |
| Indicateur de connexion | Deja present | "hors ligne" si deconnecte |
| Differentiation "moi" vs "autre" | actorId dans GridUpdated | Style visuel distinct pour les lettres que j'ai posees vs. celles posees par d'autres |

### 3.2 Ce que le Game Board NE DOIT PAS permettre

| Action interdite | Raison |
|-----------------|--------|
| Changer le type d'une cellule (LETTER -> BLACK, etc.) | Mode jeu, pas edition |
| Modifier le texte d'un indice | Mode jeu, pas edition |
| Modifier les separateurs | Mode jeu, pas edition |
| Modifier les numeros | Mode jeu, pas edition |
| Redimensionner la grille | Mode jeu, pas edition |
| Modifier le titre, la difficulte, la description | Mode jeu, pas edition |
| Modifier l'enigme globale | Mode jeu, pas edition |
| Transformer une cellule en noire via Shift | Shift change la direction en jeu, pas le type de cellule |

### 3.3 Comportement clavier en mode jeu

| Touche | Comportement |
|--------|-------------|
| Lettre (a-z, A-Z) | Saisit la lettre dans la case LETTER selectionnee, avance le curseur |
| Backspace | Efface la lettre, recule le curseur |
| Delete | Efface la lettre, ne bouge pas le curseur |
| Fleches directionnelles | Navigation entre les cases |
| Shift | Inverse la direction de saisie (RIGHTWARDS <-> DOWNWARDS). Ne transforme PAS la cellule. |
| Tab | Aucun comportement specifique (comportement navigateur par defaut) |
| Ctrl/Cmd + touche | Ignore (pas de raccourcis custom) |

### 3.4 Gestion de la grille vide au demarrage

Quand la session demarre :
- Toutes les cases LETTER ont leur `letter.value` vide (`""`)
- Les separateurs (`letter.separator`) sont presents et visibles
- Les numeros (`letter.number`) sont presents et visibles
- Les indices sont presents et lisibles
- Les cases noires sont rendues normalement
- La grille est visuellement "prete a jouer"

---

## 4. Exigences UX / Design

### 4.1 Layout du Game Board

Le layout actuel (navbar + grille centree) est conserve mais enrichi :

```
+------------------------------------------------------------------+
| [CoCro]   "Mots du printemps" par Oscar   [3 joueurs] [Quitter] |
|            Difficulte: ** | ref. Revue CoCro n.42                |
+------------------------------------------------------------------+
|                                                                    |
|   [ Enigme globale : "Celebre compositeur (5, 4)" ]              |
|   [ _ _ _ _ _   _ _ _ _ ]                                        |
|                                                                    |
|   +----+----+----+----+----+----+----+                            |
|   | -> |    |    | #  |down|    |    |                            |
|   |clue| A  | B  |####| v  |    |    |                            |
|   +----+----+----+----+----+----+----+                            |
|   |    |    |    |    |    | #  |    |                            |
|   | C  |    |    |    |    |####|    |                            |
|   +----+----+----+----+----+----+----+                            |
|   ...                                                              |
+------------------------------------------------------------------+
```

### 4.2 Pas de panneau d'edition

En mode jeu, les elements suivants de l'editeur sont **absents** :
- Barre "Parametres de la grille" (`cocro-grid-params`)
- Selecteur "Type de cellule" (`cocro-cell-type`)
- Panneau "Contenu de l'indice" (`cocro-clue-editor`)
- Panneau "Contenu de la lettre" (`cocro-letter-editor`)
- Panneau "Enigme globale" editable (`cocro-global-clue-editor`)
- Boutons "Creer la grille" et "Supprimer le brouillon"

### 4.3 Affichage des metadonnees

- **Titre** : affiche dans la navbar, en `font-family: var(--font-display)` (Caveat), taille `var(--text-xl)` ou `var(--text-2xl)`
- **Auteur** : a cote ou sous le titre, en `var(--font-ui)`, taille plus petite, format "par {auteur}"
- **Difficulte** : badge ou icone (etoiles, chiffre stylise) a cote du titre
- **Reference** : sous le titre en `var(--text-sm)`, italique
- **Description** : tooltip au survol du titre, ou petite section depliable
- **Enigme globale** : composant `<cocro-global-clue-preview>` existant, place au-dessus de la grille (en lecture seule)

### 4.4 Indicateurs de participants

- Nombre de joueurs connectes visible dans la navbar (deja present, a conserver)
- Optionnel pour cette iteration : pas de liste nominative des participants

### 4.5 Style coherent avec "L'Atelier du Cruciverbiste"

Le Game Board doit utiliser :
- Memes variables CSS que l'editeur (`--color-paper`, `--color-surface`, `--color-forest`, `--color-ink`, etc.)
- Memes composants de rendu de cellule (`cocro-grid-cell`, `cocro-letter-input`, `cocro-clue-input`)
- Meme bordure de grille (`--grid-outer-width`, `--grid-outer-color`)
- Meme animation d'apparition des lettres (`ink-appear`)
- Police de grille `var(--font-grid)` pour les lettres

### 4.6 Differentiation visuelle "moi" vs. "autre joueur"

- **Ma lettre** : couleur `var(--color-ink)` (encre noire/foncee) — j'ecris avec mon stylo
- **Lettre d'un autre** : couleur `var(--color-forest)` (vert foret) — un collaborateur ecrit
- Cette distinction existe deja dans le game board actuel (classes `game__cell--filled-me` / `game__cell--filled-other`) ; elle doit etre preservee lors de la migration vers `<cocro-grid>`

### 4.7 Selection de cellule et direction

- Le clic sur une case LETTER la selectionne et determine la direction d'apres les indices adjacents (logique existante du `GridSelectorService.selectOnClick`)
- Un second clic sur la meme case inverse la direction
- Les cases CLUE et BLACK ne sont pas selectionnables pour la saisie (le clic peut etre ignore ou simplement ne rien faire)
- La case selectionnee est visuellement mise en surbrillance (`--color-cell-selected`)
- Les cases dans la direction active sont subtilment colorees (`isOnDirection`)

---

## 5. Exigences techniques (haut niveau)

### 5.1 BFF : Envoyer la structure de la grille au client

**Probleme actuel** : Le `SessionWelcomeEvent` ne contient aucune donnee de structure de grille. Le `GetSessionStateUseCase` ne retourne que `{x, y, letter}` sans type de cellule, indices, separateurs ni numeros.

**Solution proposee** :

Le BFF doit fournir un **nouveau endpoint ou enrichir un endpoint existant** pour retourner la structure complete de la grille liee a la session. Deux options :

**Option A (recommandee) — Nouvel endpoint REST `GET /api/sessions/{shareCode}/grid-structure`** :
- Recupere la session par `shareCode`
- Utilise `session.gridId` pour recuperer la `Grid` complete via `GridRepository.findByShortId()`
- Retourne la structure de la grille (cellules avec types, indices, separateurs, numeros, metadonnees) **avec les valeurs de lettres videes** (pour ne pas reveler les solutions)
- Format de retour : identique au `GridDto` / `CellDto` existant, avec `letter.value = ""`

**Option B — Enrichir `SessionWelcome`** :
- Ajouter un champ `gridStructure` dans `SessionEvent.SessionWelcome`
- Inconvenient : alourdit significativement le message STOMP initial

**Quelle que soit l'option** :
- Les `letter.value` (solutions) des cellules LETTER doivent etre videes avant envoi au client
- Tout le reste est transmis tel quel : `type`, `separator`, `number`, `clues`, `globalClue`, metadonnees
- Les lettres jouees par les participants proviennent du `SessionGridState` (endpoint `/state` existant), pas de la structure de la grille

### 5.2 Angular : Reutiliser `<cocro-grid>` et `GridSelectorService`

**Objectif** : Le Game Board doit utiliser le meme composant `<cocro-grid>` que l'editeur pour eviter toute duplication de rendu.

**Approche** :
1. Au chargement du Game Board, recuperer la structure de la grille (endpoint 5.1)
2. Appeler `GridSelectorService.initGrid(gridStructure)` pour charger la grille dans le service
3. Utiliser `<cocro-grid>` dans le template du Game Board pour le rendu
4. Le `GridSelectorService` gere deja : selection, navigation, direction, saisie de lettres, backspace, delete
5. **Ne PAS utiliser** `onCellTypeChange()`, `onResize()`, `updateTitle()`, `updateDifficulty()`, `updateGlobalClue()`, etc. — ces methodes sont reservees a l'editeur

**Adaptation du `GridComponent` (si necessaire)** :
- Le `GridComponent` actuel ecoute Shift pour appeler `handleShift()` sur le `GridSelectorService` — en mode jeu, `handleShift()` fait deja `inverseDirection()` (pas de changement de type). Cela est correct car `onCellTypeChange` est appele depuis le composant `CellTypeComponent` de l'editeur, pas depuis le `GridComponent` lui-meme.
- Verifier que les cellules non-LETTER ne captent pas les saisies clavier (le `inputLetter` verifie deja `isCellLetter`)

**Propagation WebSocket** :
- Quand `GridSelectorService.inputLetter()` est appele, le Game Board doit intercepter le changement et envoyer la commande `PLACE_LETTER` via WebSocket (hook ou wrapper)
- Quand un `GridUpdatedEvent` arrive d'un autre joueur, le Game Board doit appeler `GridSelectorService.updateCellInGrid()` pour mettre a jour la cellule correspondante (avec `writeLetterInCell`)

### 5.3 Pas de duplication de composants de rendu

- Le rendu des cellules (LETTER, CLUE, BLACK) est gere par `<cocro-grid-cell>`, `<cocro-letter-input>`, `<cocro-clue-input>`
- Ces composants sont dans `presentation/shared/` et sont deja reutilisables
- Le Game Board ne doit **pas** recreer son propre systeme de rendu de grille

### 5.4 Gestion de l'etat hybride (structure + lettres jouees)

La grille affichee est un **merge** :
1. **Structure** : vient de l'endpoint grid-structure (types, indices, separateurs, numeros) — charge une seule fois
2. **Lettres jouees** : viennent du `SessionGridState` (endpoint `/state`) — charge au connect puis mis a jour en temps reel via `GridUpdated`

Le merge consiste a :
- Charger la structure dans le `GridSelectorService` (cellules LETTER avec `value = ""`)
- Appliquer les lettres deja jouees (du `/state`) en ecrivant dans les cellules LETTER correspondantes via `writeLetterInCell` / `updateCellInGrid`

---

## 6. Out of scope (pour cette iteration)

| Element | Raison |
|---------|--------|
| Liste nominative des participants (avatars, noms) | Itération ulterieure, UX a designer separement |
| Chat entre joueurs | Feature independante |
| Chronometre / Timer | Feature independante |
| Scoring et classement | Feature independante (statut SCORING non traite ici) |
| Verification automatique (check) | L'endpoint `/check` existe deja, l'UI d'affichage des resultats est une feature separee |
| Mode spectateur (lecture seule sans saisie) | Pas de persona identifie pour cette iteration |
| Historique des coups (undo/redo) | Complexite WebSocket, iteration ulterieure |
| Coloration par joueur (chaque joueur a sa couleur) | Itération ulterieure — pour l'instant "moi" vs "autre" suffit |
| Responsive mobile / tablette | L'editeur n'est pas responsive non plus, coherence |
| Mode sombre | Pas dans le design system actuel |
| Sons / retours haptiques | Pas dans le perimetre |

---

## 7. Criteres d'acceptation

### 7.1 Structure de la grille

- [ ] Les cases LETTER s'affichent comme dans l'editeur (meme composant `cocro-letter-input`)
- [ ] Les cases CLUE_SINGLE et CLUE_DOUBLE affichent le texte des indices (meme composant `cocro-clue-input`)
- [ ] Les cases BLACK s'affichent avec un fond noir
- [ ] Les separateurs (LEFT, UP, BOTH) sont visuellement rendus sur les cases LETTER
- [ ] Les numeros apparaissent dans le coin des cases LETTER numerotees
- [ ] Les cases LETTER commencent vides (pas de lettre-solution visible)
- [ ] L'enigme globale est affichee au-dessus de la grille si elle existe

### 7.2 Metadonnees

- [ ] Le titre de la grille est affiche dans l'en-tete
- [ ] L'auteur est affiche
- [ ] La difficulte est affichee si definie (et differente de "NONE")
- [ ] La reference est affichee si definie
- [ ] La description est accessible si definie

### 7.3 Saisie et interaction

- [ ] La saisie d'une lettre (a-z) ecrit dans la case LETTER selectionnee
- [ ] La saisie envoie une commande `PLACE_LETTER` via WebSocket
- [ ] Backspace efface et recule, Delete efface sans bouger
- [ ] Les fleches directionnelles naviguent entre les cases
- [ ] Shift inverse la direction (RIGHTWARDS <-> DOWNWARDS), ne transforme PAS en case noire
- [ ] Les cases CLUE et BLACK ne sont pas selectionnables pour la saisie
- [ ] Le clic sur une case LETTER la selectionne et determine la direction automatiquement

### 7.4 Temps reel

- [ ] Les lettres posees par d'autres joueurs apparaissent en temps reel
- [ ] Les lettres "moi" et "autre" sont visuellement differenciees
- [ ] Le nombre de participants se met a jour en temps reel (join/leave)
- [ ] Un joueur rejoignant une session en cours voit toutes les lettres deja posees

### 7.5 Backend

- [ ] Le BFF expose un moyen de recuperer la structure de la grille liee a une session (sans les lettres-solution)
- [ ] Le `SessionWelcome` ou un endpoint associe fournit les metadonnees de la grille (titre, auteur, difficulte, reference, description, globalClue)
- [ ] Les lettres-solution ne sont JAMAIS envoyees au client avant la fin de la partie

### 7.6 Non-regression

- [ ] L'editeur de grille continue de fonctionner normalement (pas de regression liee au partage du `GridSelectorService`)
- [ ] Le `GridComponent` partage fonctionne indifferemment en contexte editeur et en contexte jeu
- [ ] Les tests existants (WebSocket IT, unit tests) passent toujours

### 7.7 UX et design

- [ ] Le Game Board respecte la charte "Atelier du Cruciverbiste" (couleurs, polices, bordures)
- [ ] Pas de panneau d'edition visible en mode jeu (pas de grid-params, cell-type, clue-editor, letter-editor)
- [ ] L'experience est fluide : pas de flash blanc au chargement de la structure, animation d'entree de la grille
- [ ] La grille est centree dans la zone de jeu avec un espacement suffisant

---

## Annexe : Flux technique simplifie

```
Joueur ouvre /game/{shareCode}
  |
  ├─ 1. STOMP CONNECT + SUBSCRIBE /app/session/{shareCode}/welcome
  │     → recoit SessionWelcome (status, participantCount, gridRevision)
  │
  ├─ 2. HTTP GET /api/sessions/{shareCode}/grid-structure   (NOUVEAU)
  │     → recoit la structure complete de la grille (sans lettres-solution)
  │     → GridSelectorService.initGrid(structure)
  │     → <cocro-grid> rend la grille
  │
  ├─ 3. HTTP GET /api/sessions/{shareCode}/state
  │     → recoit les lettres deja jouees {x, y, letter}
  │     → merge dans GridSelectorService (writeLetterInCell pour chaque cellule)
  │
  ├─ 4. SUBSCRIBE /topic/session/{shareCode}
  │     → recoit GridUpdated, ParticipantJoined/Left, etc. en continu
  │
  └─ 5. Saisie clavier → GridSelectorService.inputLetter()
        → STOMP SEND /app/session/{shareCode}/grid { PLACE_LETTER, x, y, letter }
        → Serveur broadcast GridUpdated a tous
```
