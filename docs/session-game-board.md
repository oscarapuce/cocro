# Session Game Board — Documentation technique

## Vue d'ensemble

Le **Game Board** (`/game/:shareCode`) est la page de jeu collaboratif en temps réel. Elle permet à plusieurs joueurs de remplir ensemble une grille de mots fléchés. Elle se distingue du Grid Editor en étant :

- **En lecture seule pour la structure** : la grille est chargée depuis la session (pas d'édition de cellules/indices)
- **Clavier global** : toutes les frappes sont interceptées par `GameBoardComponent` (pas par la grille elle-même)
- **Temps réel** : chaque lettre posée ou effacée est propagée via WebSocket STOMP à tous les participants

---

## Architecture

### Flux de données au démarrage

```
1. ngOnInit()
   └── sessionSocket.connect(token, shareCode, onEvent)
          │
          ├── STOMP CONNECT → /ws (SockJS)
          │       connectHeaders: { Authorization: Bearer <token>, shareCode }
          │
          ├── onConnect → subscribeWelcome()
          │       subscribe /app/session/{shareCode}/welcome
          │
          └── SessionWelcome event reçu
                  ├── status, participantCount, gridRevision mis à jour
                  ├── connected = true
                  └── resync(gridRevision)
                            │
                            └── GET /api/sessions/{shareCode}/state
                                    └── cellMap rempli (lettres déjà posées)

2. subscribeTopic() — après le welcome
   ├── /topic/session/{shareCode}       → ParticipantJoined, ParticipantLeft,
   │                                      SessionStarted, GridUpdated
   └── /user/queue/session              → SyncRequired → relance resync()
```

### Flux de saisie (clavier)

```
@HostListener keydown sur .game (tabindex=0)
        │
        ├── Lettre alphanumérique [a-zA-ZÀ-ÿ]
        │       ├── placeLocalLetter(x, y, letter, 'me')  ← optimistic update
        │       ├── sendGridUpdate(PLACE_LETTER, x, y, letter)  ← WS publish
        │       └── déplace la sélection vers x+1
        │
        ├── Backspace / Delete
        │       ├── placeLocalLetter(x, y, '', 'me')
        │       └── sendGridUpdate(CLEAR_CELL, x, y)
        │
        └── ArrowRight/Left/Down/Up → déplace selectedX/selectedY
```

### Mise à jour depuis un autre joueur

```
GridUpdated event (topic STOMP)
        ├── si actorId == myUserId → ignoré (déjà appliqué en local)
        ├── revision + 1
        └── placeLocalLetter(posX, posY, letter, actorId)
                → game__cell--filled-other si actorId !== 'me'
                → game__cell--filled-me   si actorId === 'me'
```

---

## API BFF

### GET /api/sessions/{shareCode}/state

Retourne l'état courant des lettres posées dans la session. Appelé après chaque `SessionWelcome` et chaque `SyncRequired`.

- **Auth** : Bearer JWT requis, rôle `PLAYER`, `ADMIN` ou `ANONYMOUS`
- **Paramètre** : `shareCode` — code de partage de la session (ex. `AB12`)

**Réponse 200 OK :**
```json
{
  "sessionId": "sess-123",
  "shareCode": "AB12",
  "revision": 3,
  "cells": [
    { "x": 1, "y": 0, "letter": "A" },
    { "x": 2, "y": 1, "letter": "B" }
  ]
}
```

**Erreurs :**
- `404` — session introuvable
- `401` — token absent ou invalide

---

### GET /api/sessions/{shareCode}/grid-template

Retourne la structure de la grille de référence, **sans les lettres correctes** (stripped). Conçu pour le rendu côté client sans exposer les solutions.

- **Auth** : Bearer JWT requis
- **Participation** : l'utilisateur doit être un participant `JOINED` de la session
- **Use case** : `GetSessionGridTemplateUseCase`
- **Mapper** : `GridTemplateMapper.toGridTemplateDto()` — lit depuis `GridRepository`, efface les lettres au niveau du mapping

**Réponse 200 OK :**
```json
{
  "title": "Grille du dimanche",
  "width": 3,
  "height": 3,
  "difficulty": "2",
  "author": "user-123",
  "reference": "GDS-2026",
  "description": "Une grille thématique",
  "globalClueLabel": "Animal caché",
  "globalClueWordLengths": [3, 5],
  "cells": [
    { "x": 0, "y": 0, "type": "CLUE_SINGLE", "separator": null, "number": null,
      "clues": [{ "direction": "RIGHT", "text": "Fruit tropical" }] },
    { "x": 1, "y": 0, "type": "LETTER", "separator": "NONE", "number": 1, "clues": null },
    { "x": 2, "y": 0, "type": "LETTER", "separator": "LEFT", "number": null, "clues": null },
    { "x": 1, "y": 1, "type": "BLACK", "separator": null, "number": null, "clues": null }
  ]
}
```

**Types de cellules :**

| type | description |
|------|-------------|
| `LETTER` | case de saisie, contient `separator` et `number` |
| `CLUE_SINGLE` | case indice simple, contient un seul élément dans `clues` |
| `CLUE_DOUBLE` | case indice double, contient deux éléments dans `clues` |
| `BLACK` | case noire, pas de contenu |

**Erreurs :**
- `401 Unauthorized` — utilisateur non authentifié
- `400 InvalidShareCode` — format de shareCode invalide
- `404 SessionNotFound` — session introuvable
- `403 UserNotParticipant` — utilisateur non participant de la session
- `404 ReferenceGridNotFound` — grille de référence absente en base

> **Note d'implémentation** : L'adaptateur Angular `SessionGridTemplateHttpAdapter` et le mapper `mapGridTemplateToGrid()` sont en place dans `cocro-angular`. Le `GameBoardComponent` actuel ne les appelle pas encore — il affiche la grille via un rendu plat issu de `GET /state` uniquement. L'intégration complète (rendu structuré avec cellules CLUE/BLACK) est prévue dans la prochaine itération.

---

### POST /app/session/{shareCode}/grid (STOMP publish)

Publie un événement de modification de cellule vers le BFF.

**Corps du message (JSON) :**
```json
{ "posX": 1, "posY": 0, "commandType": "PLACE_LETTER", "letter": "A" }
```
ou
```json
{ "posX": 1, "posY": 0, "commandType": "CLEAR_CELL" }
```

**Types de commandes :**

| commandType | description |
|-------------|-------------|
| `PLACE_LETTER` | pose une lettre en `(posX, posY)` |
| `CLEAR_CELL` | efface la lettre en `(posX, posY)` |

---

## Comportement clavier

Le composant capture tous les événements clavier via `@HostListener('keydown')` sur le conteneur `.game` (qui doit avoir le focus). Aucun gestionnaire clavier n'est délégué aux cellules individuelles.

| Touche | Action |
|--------|--------|
| `A-Z`, `a-z`, lettres accentuées | Pose la lettre en majuscule dans la cellule sélectionnée, avance d'une colonne |
| `Backspace`, `Delete` | Efface la lettre dans la cellule sélectionnée |
| `ArrowRight` | Déplace la sélection d'une colonne vers la droite |
| `ArrowLeft` | Déplace la sélection d'une colonne vers la gauche |
| `ArrowDown` | Déplace la sélection d'une ligne vers le bas |
| `ArrowUp` | Déplace la sélection d'une ligne vers le haut |
| `Shift` + toute touche | Non traité — le Shift seul ne déclenche pas de transformation en case noire (contrairement à l'éditeur) |

---

## Composants Angular

### GameBoardComponent

**Fichier :** `cocro-angular/src/app/presentation/features/game/board/game-board.component.ts`

**Selector :** `app-game-board`

**Signaux (signals) :**

| signal | type | rôle |
|--------|------|------|
| `shareCode` | `Signal<string>` | code de la session, lu depuis `ActivatedRoute` |
| `status` | `Signal<SessionStatus>` | statut de la session (`CREATING`, `PLAYING`, …) |
| `participantCount` | `Signal<number>` | nombre de participants connectés |
| `revision` | `Signal<number>` | révision courante de la grille |
| `connected` | `Signal<boolean>` | état de la connexion WebSocket |
| `selectedX` / `selectedY` | `Signal<number>` | coordonnées de la cellule sélectionnée (-1 = aucune) |
| `gridWidth` / `gridHeight` | `Signal<number>` | dimensions de la grille (par défaut 10×10) |
| `cellMap` | `Signal<Map<string, GridCell>>` | map clé `"x,y"` → `GridCell` |
| `rows` | `computed` | tableau 2D de `GridCell` dérivé de `cellMap`, `gridWidth`, `gridHeight` |

**Cycle de vie :**

- `ngOnInit()` — lit le `shareCode` depuis la route, vérifie le token, lance la connexion WebSocket
- `ngOnDestroy()` — déconnecte le WebSocket via `sessionSocket.disconnect()`

**Méthodes publiques :**

- `selectCell(x, y)` — met à jour `selectedX` / `selectedY`
- `isSelected(x, y)` — retourne `true` si la cellule est sélectionnée
- `onKeyDown(event)` — gestionnaire clavier global
- `leave()` — appelle `leaveSession`, déconnecte le WS, navigue vers `/`

**Ports injectés :**

| token | implémentation prod |
|-------|---------------------|
| `SESSION_SOCKET_PORT` | `SessionStompAdapter` |
| `GAME_SESSION_PORT` | `GameSessionHttpAdapter` |

**Interface GridCell (interne) :**
```typescript
interface GridCell {
  x: number;
  y: number;
  letter: string;
  actorId: string;  // 'me' | userId | ''
}
```

---

### SessionStompAdapter

**Fichier :** `cocro-angular/src/app/infrastructure/adapters/session/session-stomp.adapter.ts`

Implémente `SessionSocketPort`. Gère la connexion STOMP via SockJS.

**Méthodes :**

| méthode | description |
|---------|-------------|
| `connect(token, shareCode, onEvent)` | établit la connexion, subscribe au welcome puis au topic broadcast |
| `disconnect()` | unsubscribe tout et désactive le client |
| `sendGridUpdate(shareCode, payload)` | publie vers `/app/session/{shareCode}/grid` |

**Flux de subscription :**
1. `CONNECT` établi → `subscribeWelcome()` → `/app/session/{shareCode}/welcome`
2. Réception du welcome → `subscribeTopic()`:
   - `/topic/session/{shareCode}` — events broadcast
   - `/user/queue/session` — events privés (SyncRequired)

---

### SessionGridTemplateHttpAdapter (prêt, non encore utilisé par GameBoardComponent)

**Fichier :** `cocro-angular/src/app/infrastructure/adapters/session/session-grid-template-http.adapter.ts`

Implémente `SessionGridTemplatePort`. Appelle `GET /api/sessions/{shareCode}/grid-template`.

**Mapper associé :** `grid-template.mapper.ts` — `mapGridTemplateToGrid(dto: GridTemplateResponse): Grid`

Convertit le DTO BFF en modèle `Grid` Angular, en initialisant les lettres à `''` (template vide).

---

## Structure HTML du template

```
.game [keydown, tabindex=0]
├── nav.game__nav
│   ├── span.game__brand        "CoCro"
│   ├── div.game__nav-info
│   │   ├── span.game__code     shareCode
│   │   ├── span.section-label  "{n} joueur(s)"
│   │   ├── span.game__revision "rév.{n}"
│   │   └── span.game__offline  "hors ligne" (si !connected)
│   └── cocro-button[ghost]     "Quitter"
├── hr.divider
└── main.game__board
    └── .game__grid
        └── .game__row (×height)
            └── .game__cell (×width)
                [class.game__cell--selected]
                [class.game__cell--filled-me]
                [class.game__cell--filled-other]
                └── span.game__letter (si lettre présente)
```

---

## Tests

### Tests unitaires BFF — GetSessionGridTemplateUseCaseTest

**Fichier :** `cocro-bff/src/test/kotlin/com/cocro/application/session/usecase/GetSessionGridTemplateUseCaseTest.kt`

6 cas couverts :

| # | cas de test | résultat attendu |
|---|-------------|-----------------|
| 1 | utilisateur non authentifié | `CocroResult.Error([SessionError.Unauthorized])` |
| 2 | shareCode invalide (format incorrect) | `CocroResult.Error([SessionError.InvalidShareCode])` |
| 3 | session introuvable | `CocroResult.Error([SessionError.SessionNotFound])` |
| 4 | utilisateur non participant de la session | `CocroResult.Error([SessionError.UserNotParticipant])` |
| 5 | grille de référence absente | `CocroResult.Error([SessionError.ReferenceGridNotFound])` |
| 6 | succès — grille avec LETTER/CLUE_SINGLE/CLUE_DOUBLE/BLACK | `CocroResult.Success(GridTemplateDto)` avec lettres effacées et types corrects |

### Tests e2e Playwright — game-board.spec.ts

**Fichier :** `cocro-angular/e2e/game-board.spec.ts`

**Stratégie de mock :**
- Les requêtes HTTP REST sont interceptées via `page.route()` (Playwright)
- Les requêtes WebSocket (`/ws/**`) sont abortées pour éviter le bruit réseau
- Les tests nécessitant un STOMP actif (données de grille, saisie clavier) sont marqués `test.skip()` et requièrent le BFF en cours d'exécution

**Groupes de tests :**

| groupe | stratégie | tests |
|--------|-----------|-------|
| Routing & auth guard | pas de WS | redirect vers login si non-auth, reste sur `/game/AB12` si auth |
| Layout statique | WS aborté | `.game` visible, brand, code, "hors ligne", bouton Quitter, `.game__board`, absence des panneaux d'édition, révision à 0 |
| Navigation | WS aborté | clic Quitter → retour vers `/` |
| Rendu de grille | skip (requires BFF) | `.game__grid` après SessionWelcome, `game__cell`, lettres préchargées, participantCount, révision > 0 |
| Interactions clavier | skip (requires BFF) | sélection, saisie lettre, `filled-me`, Backspace, navigation flèches, auto-avance, lettre adverse `filled-other` |

**Exécution locale (avec BFF) :**
```bash
# Démarrer l'infra
bash scripts/compose-script.sh

# Démarrer le BFF
./gradlew cocro-bff:bootRun

# Lancer tous les tests e2e
cd cocro-angular && npx playwright test e2e/game-board.spec.ts

# Lancer seulement les tests sans skip (CI-friendly)
cd cocro-angular && npx playwright test e2e/game-board.spec.ts --grep-invert "requires integration"
```

---

## Décisions architecturales

### REST pour le rechargement d'état (GET /state), WebSocket pour les événements incrémentaux

Le choix de passer par REST pour `resync()` (déclenché par `SessionWelcome` et `SyncRequired`) simplifie la logique de reprise : en cas de désynchronisation, le client recharge l'état complet sans rejouer les événements manquants. Le WebSocket est réservé aux mises à jour incrémentales temps réel (GridUpdated).

### Clavier capturé par le composant parent (pas par la grille)

Le `GameBoardComponent` possède `tabindex=0` et intercepte `keydown` via `@HostListener`. Les cellules individuelles (`.game__cell`) ne gèrent pas les événements clavier. Cela centralise la logique de navigation et évite les conflits de focus.

### Optimistic update (placeLocalLetter avant sendGridUpdate)

Quand un joueur pose une lettre, elle est immédiatement affichée en local (`actorId = 'me'`) avant que le WS confirme. Si le BFF reçoit l'événement et le broadcast, le `GridUpdated` reçu par le même joueur est ignoré (`actorId === myUserId`). Cela garantit une réactivité immédiate sans double affichage.

### Pas de SHIFT → case noire en mode jeu

L'éditeur de grille utilise `Shift` pour transformer une cellule en case noire. En mode jeu, cette transformation n'est pas disponible : la structure de la grille est fixée. Toute touche non alphanumérique et non flèche est ignorée.

### Endpoint grid-template non encore consommé par l'Angular

Le BFF expose `GET /api/sessions/{shareCode}/grid-template` (lettres effacées, structure complète avec types LETTER/CLUE/BLACK). L'adaptateur Angular et le mapper sont en place. La prochaine itération consistera à câbler cet appel dans `GameBoardComponent` pour :
- Afficher les cases CLUE avec leur texte d'indice
- Afficher les cases BLACK visuellement distinctes
- Utiliser `GridSelectorService` pour une gestion d'état typée
