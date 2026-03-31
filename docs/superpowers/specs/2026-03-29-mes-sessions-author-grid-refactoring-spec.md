# Mes Sessions, Author, Grid Refactoring & Sidebar Sessions — Spec

**Date :** 2026-03-29
**Status :** Draft
**Priorité :** v0.2.0

---

## 1. Contexte & Objectifs

### Contexte actuel

- L'app CoCro permet de créer des sessions collaboratives de mots fléchés.
- La page **"Mes grilles"** (`/grid/mine`) existe et permet de lister/éditer/lancer des sessions depuis ses grilles.
- **Aucune page "Mes sessions"** n'existe — l'utilisateur ne peut pas retrouver ses sessions en cours.
- Le champ `creatorId` dans `Session` est un simple `UserId` (UUID) — impossible d'afficher un nom lisible.
- Le champ `metadata.author` dans `Grid` est aussi un `UserId` brut.
- Le `title` de la grille est au top-level de `Grid` au lieu d'être dans `metadata`.
- `width`/`height` sont deux champs séparés au lieu d'un value object `GridDimension`.
- La sidebar a un lien "Session" unique qui pointe vers `/lobby/create` — pas de sous-menu.

### Objectifs

| # | Objectif | Impact |
|---|----------|--------|
| 1 | Page **"Mes Sessions"** : lister, re-rejoindre, supprimer | UX |
| 2 | Objet **`Author(id, username)`** dans Session ET Grid | Domain / Mongo / DTOs |
| 3 | Déplacer `title` dans `GridMetadata` | Domain refactoring |
| 4 | Grouper `width`/`height` en **`GridDimension`** | Domain refactoring |
| 5 | Ajouter **`globalClue`** optionnel dans Grid (objet dédié) | Domain |
| 6 | **Sidebar Sessions** collapsible : Créer, Rejoindre, Mes Sessions | UX / Angular |

---

## 2. Architecture des changements

### 2.1 BFF — Domain (`com.cocro.domain`)

#### 2.1.1 Nouveau value object : `Author`

```kotlin
// domain/common/model/Author.kt
package com.cocro.domain.common.model

import com.cocro.domain.auth.model.valueobject.UserId

data class Author(
    val id: UserId,
    val username: String,
)
```

#### 2.1.2 Nouveau value object : `GridDimension`

```kotlin
// domain/grid/model/valueobject/GridDimension.kt
package com.cocro.domain.grid.model.valueobject

data class GridDimension(
    val width: GridWidth,
    val height: GridHeight,
)
```

#### 2.1.3 Refactoring `GridMetadata` — ajout de `title` et `globalClue`

```kotlin
// AVANT
data class GridMetadata(
    val author: UserId,
    val reference: String?,
    val description: String?,
    val difficulty: String = "NONE",
    val globalClueLabel: String? = null,
    val globalClueWordLengths: List<Int>? = null,
)

// APRÈS
data class GridMetadata(
    val title: GridTitle,             // ← déplacé depuis Grid
    val author: Author,               // ← enrichi UserId → Author
    val reference: String?,
    val description: String?,
    val difficulty: String = "NONE",
    val globalClue: GlobalClue? = null, // ← objet dédié remplace les 2 champs
)
```

#### 2.1.4 Nouveau value object : `GlobalClue`

```kotlin
// domain/grid/model/GlobalClue.kt
package com.cocro.domain.grid.model

data class GlobalClue(
    val label: String,
    val wordLengths: List<Int>,
)
```

#### 2.1.5 Refactoring `Grid`

```kotlin
// AVANT
data class Grid(
    val id: UUID,
    val shortId: GridShareCode,
    val title: GridTitle,          // ← déplacé dans metadata
    val metadata: GridMetadata,
    val hashLetters: Long,
    val width: GridWidth,          // ← fusionnés
    val height: GridHeight,        // ← fusionnés
    val cells: List<Cell>,
    val createdAt: Instant,
    val updatedAt: Instant,
)

// APRÈS
data class Grid(
    val id: UUID,
    val shortId: GridShareCode,
    val metadata: GridMetadata,     // contient title, author, globalClue
    val hashLetters: Long,
    val dimension: GridDimension,   // width + height groupés
    val cells: List<Cell>,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    // Helpers d'accès rapide
    val title: GridTitle get() = metadata.title
    val width: GridWidth get() = dimension.width
    val height: GridHeight get() = dimension.height
}
```

#### 2.1.6 Refactoring `Session` — `creatorId` → `author`

```kotlin
// AVANT
data class Session(
    ...
    val creatorId: UserId,
    ...
)

// APRÈS
data class Session(
    ...
    val author: Author,   // id + username
    ...
) {
    val creatorId: UserId get() = author.id  // rétro-compat helpers
}
```

**Impact `Session.create()` et `Session.rehydrate()`** : le paramètre `creatorId: UserId` devient `author: Author`.

#### 2.1.7 Nouvelle erreur : `NotCreator`

```kotlin
// domain/session/error/SessionError.kt — ajouter
data class NotCreator(
    val userId: String,
    val shareCode: String,
) : SessionError {
    override val errorCode = ErrorCode.SESSION_NOT_CREATOR
    override fun context() = mapOf("userId" to userId, "shareCode" to shareCode)
}
```

```kotlin
// domain/common/error/ErrorCode.kt — ajouter
SESSION_NOT_CREATOR
```

---

### 2.2 BFF — Application (`com.cocro.application`)

#### 2.2.1 Nouveau DTO : `SessionSummaryDto`

```kotlin
// application/session/dto/SessionSummaryDto.kt
data class SessionSummaryDto(
    val sessionId: String,
    val shareCode: String,
    val status: String,           // PLAYING, INTERRUPTED, ENDED
    val gridTitle: String,
    val gridDimension: DimensionDto,
    val authorName: String,       // username du créateur
    val participantCount: Int,
    val role: String,             // "CREATOR" | "PARTICIPANT"
    val createdAt: String,        // ISO-8601
    val updatedAt: String,
)

data class DimensionDto(
    val width: Int,
    val height: Int,
)
```

#### 2.2.2 Port `SessionRepository` — nouvelles méthodes

```kotlin
// application/session/port/SessionRepository.kt — ajouter
fun findByCreator(authorId: UserId): List<Session>
fun findByParticipantUserId(userId: UserId): List<Session>
fun deleteById(sessionId: SessionId)
```

#### 2.2.3 Nouveau use case : `GetMySessionsUseCase`

```kotlin
// application/session/usecase/GetMySessionsUseCase.kt
@Component
class GetMySessionsUseCase(
    private val sessionRepository: SessionRepository,
) {
    fun execute(userId: UserId): List<SessionSummaryDto> {
        val created = sessionRepository.findByCreator(userId)
            .map { it.toSummary("CREATOR") }
        val joined = sessionRepository.findByParticipantUserId(userId)
            .filter { it.author.id != userId } // exclure celles où on est aussi créateur
            .map { it.toSummary("PARTICIPANT") }
        return (created + joined).sortedByDescending { it.updatedAt }
    }
}
```

#### 2.2.4 Nouveau use case : `DeleteSessionUseCase`

```kotlin
// application/session/usecase/DeleteSessionUseCase.kt
@Component
class DeleteSessionUseCase(
    private val sessionRepository: SessionRepository,
    private val sessionGridStateCache: SessionGridStateCache,
    private val heartbeatTracker: HeartbeatTracker,
) {
    fun execute(shareCode: String, actorId: UserId): CocroResult<Unit, SessionError> {
        if (!SessionShareCodeRule.validate(shareCode)) {
            return CocroResult.Error(listOf(SessionError.InvalidShareCode(shareCode)))
        }
        val session = sessionRepository.findByShareCode(SessionShareCode(shareCode))
            ?: return CocroResult.Error(listOf(SessionError.SessionNotFound(shareCode)))

        if (session.author.id != actorId) {
            return CocroResult.Error(listOf(SessionError.NotCreator(actorId.toString(), shareCode)))
        }

        // Nettoyage cache Redis + heartbeats
        sessionGridStateCache.evict(session.id)
        heartbeatTracker.removeAll(session.id)
        sessionRepository.deleteById(session.id)

        return CocroResult.Success(Unit)
    }
}
```

#### 2.2.5 Mapper `Session → SessionSummaryDto`

```kotlin
// application/session/mapper/SessionSummaryMapper.kt
fun Session.toSummary(role: String): SessionSummaryDto {
    val template = gridTemplate
    return SessionSummaryDto(
        sessionId = id.toString(),
        shareCode = shareCode.toString(),
        status = status.name,
        gridTitle = template?.title ?: "Sans titre",
        gridDimension = DimensionDto(
            width = template?.width ?: 0,
            height = template?.height ?: 0,
        ),
        authorName = author.username,
        participantCount = ParticipantsRule.countActiveParticipants(participants),
        role = role,
        createdAt = createdAt.toString(),
        updatedAt = updatedAt.toString(),
    )
}
```

---

### 2.3 BFF — Infrastructure (`com.cocro.infrastructure`)

#### 2.3.1 MongoDB Documents — changements

**`SessionDocument`** :

```kotlin
// AVANT
val creatorId: String

// APRÈS
val authorId: String       // UUID string
val authorUsername: String  // nom lisible
```

**`GridDocument`** :

```kotlin
// AVANT
val title: String
val width: Int
val height: Int

// APRÈS — pas de changement structurel Mongo
// Le title reste au top-level dans le document pour la rétro-compat
// Le mapper fait la traduction vers metadata.title dans le domaine
// width/height restent des champs plats, le mapper groupe en GridDimension
```

**`GridMetadataDocument`** :

```kotlin
// AVANT
val author: String   // UUID

// APRÈS
val authorId: String
val authorUsername: String
val globalClueLabel: String? = null         // inchangé
val globalClueWordLengths: List<Int>? = null // inchangé
```

#### 2.3.2 Nouveaux index MongoDB

```kotlin
// SessionDocument — ajouter
@CompoundIndex(name = "authorId_idx", def = "{'authorId': 1}")
@CompoundIndex(name = "participants_userId_idx", def = "{'participants.userId': 1}")
```

#### 2.3.3 `SpringDataSessionRepository` — nouvelles queries

```kotlin
fun findByAuthorId(authorId: String): List<SessionDocument>
fun findByParticipantsUserId(userId: String): List<SessionDocument>
fun deleteById(id: String)
```

#### 2.3.4 `MongoSessionRepositoryAdapter` — implémenter les nouvelles méthodes

```kotlin
override fun findByCreator(authorId: UserId): List<Session> =
    springDataRepo.findByAuthorId(authorId.toString()).map { it.toDomain() }

override fun findByParticipantUserId(userId: UserId): List<Session> =
    springDataRepo.findByParticipantsUserId(userId.toString()).map { it.toDomain() }

override fun deleteById(sessionId: SessionId) {
    springDataRepo.deleteById(sessionId.value.toString())
}
```

#### 2.3.5 Mappers Mongo — mise à jour

**`SessionDocumentMapper`** :

```kotlin
// toDocument() : creatorId → authorId + authorUsername
fun Session.toDocument(): SessionDocument =
    SessionDocument(
        ...
        authorId = author.id.toString(),
        authorUsername = author.username,
        ...
    )

// toDomain() : authorId + authorUsername → Author
fun SessionDocument.toDomain(): Session =
    Session.rehydrate(
        ...
        author = Author(
            id = UserId.from(authorId),
            username = authorUsername,
        ),
        ...
    )
```

**`GridDocumentMapper`** :

```kotlin
// toDocument() : metadata.author → authorId + authorUsername
metadata = GridMetadataDocument(
    authorId = metadata.author.id.toString(),
    authorUsername = metadata.author.username,
    ...
)

// toDomain() : titre du doc → metadata.title, width/height → dimension
fun GridDocument.toDomain(): Grid =
    Grid(
        ...
        metadata = GridMetadata(
            title = GridTitle(title),
            author = Author(
                id = UserId.from(metadata.authorId),
                username = metadata.authorUsername,
            ),
            globalClue = if (metadata.globalClueLabel != null)
                GlobalClue(metadata.globalClueLabel, metadata.globalClueWordLengths ?: emptyList())
            else null,
            ...
        ),
        dimension = GridDimension(
            width = GridWidth(width),
            height = GridHeight(height),
        ),
        ...
    )
```

#### 2.3.6 Migration MongoDB — stratégie graceful

Pas de script de migration. Les mappers gèrent les anciens documents :

```kotlin
// SessionDocument.toDomain() — fallback pour ancien format
author = Author(
    id = UserId.from(authorId ?: creatorId),  // fallback champ legacy
    username = authorUsername ?: "Inconnu",
),
```

```kotlin
// GridMetadataDocument — champs nullable pour rétro-compat
data class GridMetadataDocument(
    val authorId: String? = null,
    val authorUsername: String? = null,
    val author: String? = null,  // legacy, ignoré si authorId existe
    ...
)
```

---

### 2.4 BFF — Presentation (REST)

#### 2.4.1 Nouveaux endpoints

```
GET  /api/sessions/mine          → List<SessionSummaryDto>
DELETE /api/sessions/{shareCode} → 204 No Content
```

```kotlin
// SessionController.kt — ajouter

@GetMapping("/mine")
@PreAuthorize("hasAnyRole('PLAYER', 'ADMIN')")
fun getMySessions(authentication: Authentication): ResponseEntity<*> {
    val userId = (authentication as CocroAuthentication).user.userId
    val sessions = getMySessionsUseCase.execute(userId)
    return ResponseEntity.ok(sessions)
}

@DeleteMapping("/{shareCode}")
@PreAuthorize("hasAnyRole('PLAYER', 'ADMIN')")
fun deleteSession(
    @PathVariable shareCode: String,
    authentication: Authentication,
): ResponseEntity<*> {
    val userId = (authentication as CocroAuthentication).user.userId
    return deleteSessionUseCase
        .execute(shareCode, userId)
        .toResponseEntity(HttpStatus.NO_CONTENT)
}
```

---

### 2.5 Angular — Domain models

#### 2.5.1 Nouveau modèle : `SessionSummary`

```typescript
// domain/models/session-summary.model.ts
export interface SessionSummary {
  sessionId: string;
  shareCode: string;
  status: 'PLAYING' | 'INTERRUPTED' | 'ENDED';
  gridTitle: string;
  gridDimension: { width: number; height: number };
  authorName: string;
  role: 'CREATOR' | 'PARTICIPANT';
  createdAt: string;
  updatedAt: string;
}
```

#### 2.5.2 Mise à jour `GridSummary`

```typescript
// domain/models/grid-summary.model.ts — ajouter
export interface GridSummary {
  gridId: string;
  title: string;
  width: number;
  height: number;
  difficulty: string;
  authorName: string;     // ← nouveau
  createdAt: string;
  updatedAt: string;
}
```

---

### 2.6 Angular — Infrastructure

#### 2.6.1 Nouveau service HTTP : `SessionHttpAdapter`

```typescript
// infrastructure/http/session-http.adapter.ts
@Injectable({ providedIn: 'root' })
export class SessionHttpAdapter {
  private http = inject(HttpClient);

  getMySessions(): Observable<SessionSummary[]> {
    return this.http.get<SessionSummary[]>(`${environment.apiBaseUrl}/sessions/mine`);
  }

  deleteSession(shareCode: string): Observable<void> {
    return this.http.delete<void>(`${environment.apiBaseUrl}/sessions/${shareCode}`);
  }
}
```

---

### 2.7 Angular — Presentation

#### 2.7.1 Nouveau composant : `MySessionsComponent`

- Route : `/lobby/mine`
- Layout : réutilise `LandingHomeShellComponent` (single panel, showTopNav)
- Affiche deux sections : **Mes sessions créées** et **Sessions rejointes**
- Chaque carte de session affiche : titre grille, dimensions, status badge, auteur, date, actions

**Actions par carte :**

| Condition | Actions disponibles |
|---|---|
| `role=CREATOR` + `status=PLAYING\|INTERRUPTED` | Rejoindre, Supprimer |
| `role=CREATOR` + `status=ENDED` | Supprimer |
| `role=PARTICIPANT` + `status=PLAYING\|INTERRUPTED` | Rejoindre |
| `role=PARTICIPANT` + `status=ENDED` | *(aucune)* |

#### 2.7.2 Routes — `lobby.routes.ts`

```typescript
export const LOBBY_ROUTES: Routes = [
  {
    path: 'create',
    canActivate: [playerGuard],
    loadComponent: () =>
      import('./create/create-session.component').then(m => m.CreateSessionComponent),
  },
  {
    path: 'mine',
    title: 'Mes sessions',
    canActivate: [playerGuard],
    loadComponent: () =>
      import('./my-sessions/my-sessions.component').then(m => m.MySessionsComponent),
  },
  {
    path: '',
    redirectTo: 'mine',
    pathMatch: 'full',
  },
];
```

#### 2.7.3 Sidebar — sous-menu Sessions collapsible

Pattern identique au menu "Grilles" existant :

```html
<!-- auth-sidebar.component.html — remplacer le lien Session unique -->
<div class="auth-sidebar__group" [class.auth-sidebar__group--open]="sessionMenuOpen()">
  <button
    type="button"
    class="auth-sidebar__link auth-sidebar__group-toggle"
    (click)="toggleSessionMenu()"
  >
    <span>Sessions</span>
    <svg class="auth-sidebar__chevron" ...>
      <polyline points="4,6 8,10 12,6"/>
    </svg>
  </button>
  <div class="auth-sidebar__submenu">
    <a routerLink="/lobby/mine" class="auth-sidebar__sublink" (click)="onNavigate()">
      <span>Mes sessions</span>
    </a>
    <a routerLink="/lobby/create" class="auth-sidebar__sublink" (click)="onNavigate()">
      <span>Créer</span>
    </a>
    <a routerLink="/" class="auth-sidebar__sublink" (click)="onNavigate()">
      <span>Rejoindre</span>
    </a>
  </div>
</div>
```

```typescript
// auth-sidebar.component.ts — ajouter
readonly sessionMenuOpen = signal(false);

toggleSessionMenu(): void {
  this.sessionMenuOpen.update(v => !v);
}
```

---

## 3. Plan d'agents

### Agent 1 — PO (Opus) : Validation & Priorisation

**Responsabilités :**
- Valider les critères d'acceptation
- Prioriser les dépendances entre features (Author en premier, car bloquant pour "Mes Sessions")
- Valider le plan de migration MongoDB
- Rédiger les user stories dans le backlog

**Livrables :**
- User stories validées
- Critères d'acceptation revus
- MAJ de `docs/roadmap/v0.2.0-backlog.md`

### Agent 2 — Lead Dev (Opus) : Architecture & Code Review

**Responsabilités :**
- Valider l'architecture domain/application/infra
- Définir l'ordre d'implémentation
- Review du code produit par les dev agents
- Garantir la cohérence avec le pattern existant (`CocroResult`, ports, mappers)

**Ordre d'implémentation recommandé :**
1. `Author` value object + `GlobalClue` + `GridDimension` (domain)
2. Refactoring `Grid` et `GridMetadata` (domain + mappers + documents)
3. Refactoring `Session` (domain + mappers + documents)
4. Nouveaux ports + use cases (`GetMySessions`, `DeleteSession`)
5. REST endpoints
6. Angular models + HTTP adapter
7. Sidebar sessions
8. `MySessionsComponent`

### Agent 3 — Senior UI/UX (Opus) : Design des composants

**Responsabilités :**
- Design de la page "Mes sessions" (layout, cartes, états vide/loading/erreur)
- Design du badge de status (PLAYING = vert, INTERRUPTED = orange, ENDED = gris)
- Design du sous-menu sidebar Sessions
- Design de la modale de confirmation de suppression
- Respect de la charte CoCro (beige/paper + forest green, style Séyès)

**Livrables :**
- Maquette des cartes session (HTML/SCSS)
- Tokens CSS si nécessaires
- States : empty, loading, error, populated

### Agent 4 — Dev BFF (Sonnet) : Backend

**Responsabilités :**
- Implémenter les value objects (`Author`, `GridDimension`, `GlobalClue`)
- Refactoring `Grid`, `GridMetadata`, `Session` (domain)
- Mise à jour des documents Mongo et mappers
- Nouveaux ports et use cases
- Endpoints REST
- Migration graceful (mappers rétro-compat)

**Fichiers impactés (BFF) :**

| Couche | Fichiers | Action |
|--------|----------|--------|
| Domain | `Author.kt` | Créer |
| Domain | `GridDimension.kt` | Créer |
| Domain | `GlobalClue.kt` | Créer |
| Domain | `GridMetadata.kt` | Modifier (title, Author, GlobalClue) |
| Domain | `Grid.kt` | Modifier (retirer title/width/height, ajouter dimension) |
| Domain | `Session.kt` | Modifier (creatorId → author) |
| Domain | `SessionError.kt` | Ajouter NotCreator |
| Domain | `ErrorCode.kt` | Ajouter SESSION_NOT_CREATOR |
| Application | `SessionSummaryDto.kt` | Créer |
| Application | `SessionSummaryMapper.kt` | Créer |
| Application | `GetMySessionsUseCase.kt` | Créer |
| Application | `DeleteSessionUseCase.kt` | Créer |
| Application | `SessionRepository.kt` (port) | Modifier (3 nouvelles méthodes) |
| Infrastructure | `SessionDocument.kt` | Modifier (authorId + authorUsername) |
| Infrastructure | `GridMetadataDocument.kt` | Modifier (authorId + authorUsername) |
| Infrastructure | `SessionDocumentMapper.kt` | Modifier |
| Infrastructure | `GridDocumentMapper.kt` | Modifier |
| Infrastructure | `SpringDataSessionRepository.kt` | Modifier (3 queries) |
| Infrastructure | `MongoSessionRepositoryAdapter.kt` | Modifier |
| Presentation | `SessionController.kt` | Modifier (2 endpoints) |
| Presentation | `ErrorMapper.kt` | Modifier (NotCreator → 403) |

### Agent 5 — Dev Angular (Sonnet) : Frontend

**Responsabilités :**
- Modèle `SessionSummary`
- Service `SessionHttpAdapter`
- Composant `MySessionsComponent` + route
- Sidebar sessions collapsible
- MAJ `GridSummary` avec `authorName`

**Fichiers impactés (Angular) :**

| Couche | Fichiers | Action |
|--------|----------|--------|
| Domain | `session-summary.model.ts` | Créer |
| Domain | `grid-summary.model.ts` | Modifier |
| Infrastructure | `session-http.adapter.ts` | Créer |
| Presentation | `my-sessions.component.ts/html/scss` | Créer |
| Presentation | `lobby.routes.ts` | Modifier |
| Presentation | `auth-sidebar.component.ts/html` | Modifier |

### Agent 6 — QA (Sonnet) : Tests & Documentation

**Responsabilités :**
- Tests unitaires Kotlin (domain rules, use cases)
- Tests d'intégration (Testcontainers MongoDB)
- Tests Jest (composants Angular)
- Tests E2E (Playwright)
- Mise à jour documentation

---

## 4. Plan de tests

### 4.1 Tests Unitaires — BFF (JUnit 5 + AssertJ)

| Test | Fichier | Vérifie |
|------|---------|---------|
| `Author` construction | `AuthorTest.kt` | id + username non-blanks |
| `GridDimension` construction | `GridDimensionTest.kt` | width > 0, height > 0 |
| `GlobalClue` construction | `GlobalClueTest.kt` | label non-blank, wordLengths non-empty |
| `Session.create()` avec Author | `SessionTest.kt` | author.id et author.username persistés |
| `GetMySessionsUseCase` | `GetMySessionsUseCaseTest.kt` | Retourne created + joined, trié par date, exclut doublons |
| `DeleteSessionUseCase` — success | `DeleteSessionUseCaseTest.kt` | Supprime si creator |
| `DeleteSessionUseCase` — not creator | `DeleteSessionUseCaseTest.kt` | Retourne `NotCreator` |
| `DeleteSessionUseCase` — not found | `DeleteSessionUseCaseTest.kt` | Retourne `SessionNotFound` |
| `Session.toSummary()` mapper | `SessionSummaryMapperTest.kt` | Tous les champs mappés correctement |

### 4.2 Tests d'Intégration — BFF (Testcontainers)

| Test | Fichier | Vérifie |
|------|---------|---------|
| `findByCreator` | `MongoSessionRepositoryIT.kt` | Requête MongoDB + index |
| `findByParticipantUserId` | `MongoSessionRepositoryIT.kt` | Requête sur nested `participants.userId` |
| `deleteById` | `MongoSessionRepositoryIT.kt` | Suppression effective + vérif absente |
| `GET /api/sessions/mine` | `SessionControllerIT.kt` | Auth + réponse JSON valide |
| `DELETE /api/sessions/{code}` | `SessionControllerIT.kt` | 204 si creator, 403 si non-creator |
| Rétro-compat mapper | `SessionDocumentMapperIT.kt` | Ancien format (creatorId) → Author("Inconnu") |

### 4.3 Tests Jest — Angular

| Test | Fichier | Vérifie |
|------|---------|---------|
| `SessionHttpAdapter.getMySessions()` | `session-http.adapter.spec.ts` | Appel GET + mapping |
| `SessionHttpAdapter.deleteSession()` | `session-http.adapter.spec.ts` | Appel DELETE |
| `MySessionsComponent` — loading | `my-sessions.component.spec.ts` | Affiche spinner |
| `MySessionsComponent` — empty | `my-sessions.component.spec.ts` | Affiche état vide |
| `MySessionsComponent` — with data | `my-sessions.component.spec.ts` | Affiche cartes avec title, status, actions |
| `MySessionsComponent` — delete | `my-sessions.component.spec.ts` | Confirmation + appel delete + refresh |
| `MySessionsComponent` — rejoin | `my-sessions.component.spec.ts` | Navigate to `/play/:code` |
| Sidebar sessionMenu | `auth-sidebar.component.spec.ts` | Toggle open/close, liens corrects |

### 4.4 Tests E2E — Playwright

| Test | Fichier | Scénario |
|------|---------|----------|
| Flow "Mes Sessions" | `my-sessions.spec.ts` | Login → sidebar Sessions → Mes sessions → voir la liste |
| Rejoindre depuis Mes Sessions | `my-sessions.spec.ts` | Clic "Rejoindre" → navigation `/play/:code` |
| Supprimer une session | `my-sessions.spec.ts` | Clic "Supprimer" → confirm → session disparaît |
| Sidebar navigation | `sidebar-sessions.spec.ts` | Clic "Sessions" → 3 sous-liens visibles |

---

## 5. Migration MongoDB

### Stratégie : Graceful Degradation (pas de script)

Les documents existants en base ne seront **pas** migrés par un script batch. Les mappers géreront les deux formats :

| Champ ancien | Champ nouveau | Fallback mapper |
|---|---|---|
| `SessionDocument.creatorId` | `SessionDocument.authorId` | `authorId ?: creatorId` |
| *(absent)* | `SessionDocument.authorUsername` | `"Inconnu"` |
| `GridMetadataDocument.author` (string UUID) | `GridMetadataDocument.authorId` + `authorUsername` | `authorId ?: author`, `authorUsername ?: "Inconnu"` |

Les nouveaux documents créés après le déploiement auront le format complet. Les anciens seront enrichis **paresseusement** lors du prochain `save()`.

---

## 6. Critères d'acceptation

### Feature 1 — Mes Sessions

- [ ] `GET /api/sessions/mine` retourne les sessions créées ET rejointes par l'utilisateur
- [ ] Chaque session affiche : titre grille, dimensions, statut, nom auteur, nb participants, date
- [ ] Le rôle (`CREATOR` vs `PARTICIPANT`) est indiqué
- [ ] Clic "Rejoindre" sur une session PLAYING/INTERRUPTED navigue vers `/play/:shareCode`
- [ ] Clic "Supprimer" sur une session dont on est l'auteur supprime la session (avec confirmation)
- [ ] Un non-créateur ne peut pas supprimer (403)
- [ ] État vide affiché si aucune session

### Feature 2 — Author

- [ ] `Session` et `Grid` contiennent un objet `Author(id, username)` au lieu d'un simple UUID
- [ ] L'affichage du nom auteur fonctionne partout (Mes grilles, Mes sessions, cartes)
- [ ] Les anciens documents MongoDB (format UUID) sont lus sans erreur (fallback "Inconnu")

### Feature 3 — Grid Refactoring

- [ ] `title` est dans `GridMetadata`
- [ ] `width`/`height` sont dans `GridDimension`
- [ ] `GlobalClue` est un objet dédié dans `GridMetadata` (optionnel)
- [ ] Tous les tests existants passent après refactoring
- [ ] L'éditeur de grille et "Mes grilles" fonctionnent toujours

### Feature 4 — Sidebar Sessions

- [ ] Le lien "Session" dans la sidebar s'ouvre en sous-menu collapsible
- [ ] Trois sous-liens : "Mes sessions", "Créer", "Rejoindre"
- [ ] Pattern visuel identique au menu "Grilles"
- [ ] Le lien "Rejoindre" pointe vers la homepage (`/`)

---

## 7. Dépendances & Ordre d'exécution

```
┌─────────────────────────────┐
│ Phase 1 — Domain refactoring│
│                             │
│  Author, GridDimension,     │
│  GlobalClue, GridMetadata,  │
│  Grid, Session              │
└──────────┬──────────────────┘
           │
┌──────────▼──────────────────┐
│ Phase 2 — Infra & Mappers   │
│                             │
│  Documents Mongo, Mappers,  │
│  SpringData queries,        │
│  Index MongoDB              │
└──────────┬──────────────────┘
           │
┌──────────▼──────────────────┐
│ Phase 3 — Application       │
│                             │
│  GetMySessionsUseCase,      │
│  DeleteSessionUseCase,      │
│  SessionSummaryMapper       │
└──────────┬──────────────────┘
           │
┌──────────▼──────────────────┐
│ Phase 4 — Presentation      │
│                             │
│  REST endpoints,            │
│  Angular models + services, │
│  MySessionsComponent,       │
│  Sidebar Sessions           │
└──────────┬──────────────────┘
           │
┌──────────▼──────────────────┐
│ Phase 5 — QA                │
│                             │
│  TU, IT, Jest, E2E,        │
│  Documentation              │
└─────────────────────────────┘
```

---

## 8. Documentation à mettre à jour

| Fichier | Changement |
|---------|-----------|
| `docs/roadmap/v0.2.0-backlog.md` | Marquer "Mes sessions" comme spécifié |
| `docs/sessions/lifecycle.md` | Ajouter le flow de suppression |
| `docs/bff/persistence.md` | Documenter les nouveaux index et le fallback migration |
| `CLAUDE.md` | Ajouter les nouveaux endpoints et concepts |

