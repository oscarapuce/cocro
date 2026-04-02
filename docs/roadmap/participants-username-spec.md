# Spec — Participants visibles avec username & statut

**Date :** 2026-04-01
**Status :** Implemented
**Priorité :** v0.2.0
**Prérequis :** aucun

---

## 1. Contexte & Problème

Aujourd'hui, le `Participant` domain ne contient que `userId` (UUID) et `status` (JOINED/LEFT). Le `SessionFullDto` renvoyé au client ne contient qu'un `participantCount` (nombre entier). Le client **ne connaît ni les noms ni les statuts individuels** des participants.

Résultat : impossible d'afficher dans le composant de jeu un panneau listant "qui est dans la session".

### État actuel

```kotlin
// Domain
data class Participant(val userId: UserId, val status: ParticipantStatus)

// DTO renvoyé au client
data class SessionFullDto(
    val sessionId: String,
    val shareCode: String,
    val status: String,
    val participantCount: Int,    // ← juste un nombre
    // ... gridTemplate, cells, etc.
)

// Events WebSocket
data class ParticipantJoined(val userId: String, val participantCount: Int)  // ← UUID
data class ParticipantLeft(val userId: String, val participantCount: Int, val reason: String)
```

---

## 2. Objectif

Permettre au frontend d'afficher une liste des participants avec :
- **Username** (ex: "Cardamome-Dorée", "Oscar")
- **Statut** (JOINED = connecté, LEFT = parti)
- **Rôle** dans la session (CREATOR / PARTICIPANT)

---

## 3. Changements BFF

### 3.1 Domain — `Participant`

Ajouter `username: String` au `Participant` :

```kotlin
data class Participant(
    val userId: UserId,
    val username: String,
    val status: ParticipantStatus,
) {
    companion object {
        fun joined(userId: UserId, username: String) = Participant(userId, username, ParticipantStatus.JOINED)
    }
}
```

### 3.2 Domain — `SessionLifecycleCommand.Join`

Ajouter `username` au command :

```kotlin
sealed interface SessionLifecycleCommand {
    data class Join(val actorId: UserId, val username: String) : SessionLifecycleCommand
    data class Leave(val actorId: UserId) : SessionLifecycleCommand
}
```

### 3.3 Domain — `Session.join()` et `Session.applyJoin()`

Passer le `username` lors du join :

```kotlin
private fun applyJoin(actorId: UserId, username: String): CocroResult<Session, SessionError> {
    // ... existing validations ...
    val updated = join(actorId, username)
    // ...
}

fun join(actorId: UserId, username: String, now: Instant = Instant.now()): Session {
    val leftIndex = participants.indexOfFirst { it.userId == actorId && it.status == ParticipantStatus.LEFT }
    val updatedParticipants = if (leftIndex >= 0) {
        participants.mapIndexed { i, p -> if (i == leftIndex) p.copy(status = ParticipantStatus.JOINED) else p }
    } else {
        participants + Participant.joined(actorId, username)
    }
    return copy(participants = updatedParticipants, updatedAt = now)
}
```

### 3.4 Application — `SessionFullDto` + nouveau `ParticipantDto`

Nouveau DTO :

```kotlin
data class ParticipantDto(
    val userId: String,
    val username: String,
    val status: String,        // "JOINED" ou "LEFT"
    val isCreator: Boolean,
)
```

Modifier `SessionFullDto` :

```kotlin
data class SessionFullDto(
    // ... existing fields ...
    val participantCount: Int,               // garder pour rétro-compat
    val participants: List<ParticipantDto>,   // ← NOUVEAU
)
```

### 3.5 Application — Mapper `toSessionFullDto()`

```kotlin
internal fun Session.toSessionFullDto(gridState: SessionGridState, activeParticipantCount: Int): SessionFullDto {
    // ... existing ...
    return SessionFullDto(
        // ... existing fields ...
        participantCount = activeParticipantCount,
        participants = this.participants.map { p ->
            ParticipantDto(
                userId = p.userId.toString(),
                username = p.username,
                status = p.status.name,
                isCreator = p.userId == this.author.id,
            )
        },
    )
}
```

### 3.6 Application — Use cases qui appellent `join()`

| Use case | Changement |
|---|---|
| `JoinSessionUseCase` | Passer `user.username` à `SessionLifecycleCommand.Join(user.userId, user.username)` |
| `CreateSessionUseCase` | Si le créateur est auto-joiné, passer son `username` |

### 3.7 Events WebSocket — Ajouter `username`

```kotlin
data class ParticipantJoined(
    val userId: String,
    val username: String,          // ← NOUVEAU
    val participantCount: Int,
) : SessionEvent

data class ParticipantLeft(
    val userId: String,
    val username: String,          // ← NOUVEAU
    val participantCount: Int,
    val reason: String = "explicit",
) : SessionEvent

data class SessionWelcome(
    val shareCode: String,
    val topicToSubscribe: String,
    val participantCount: Int,
    val participants: List<ParticipantDto>,  // ← NOUVEAU
    val status: String,
    val gridRevision: Long,
) : SessionEvent
```

### 3.8 Infrastructure — MongoDB `SessionDocument`

Le `ParticipantDocument` doit aussi stocker le `username` :

```kotlin
data class ParticipantDocument(
    val userId: String,
    val username: String,       // ← NOUVEAU
    val status: String,
)
```

Mapper rétro-compat : si `username` est null (ancien document), mapper vers `"Inconnu"`.

---

## 4. Changements Angular

### 4.1 Domain models

```typescript
// domain/models/session.model.ts
export interface ParticipantInfo {
  userId: string;
  username: string;
  status: 'JOINED' | 'LEFT';
  isCreator: boolean;
}

export interface SessionFullResponse {
  // ... existing fields ...
  participantCount: number;
  participants: ParticipantInfo[];   // ← NOUVEAU
}
```

### 4.2 Events models

```typescript
// domain/models/session-events.model.ts
export interface ParticipantJoinedEvent {
  type: 'ParticipantJoined';
  userId: string;
  username: string;          // ← NOUVEAU
  participantCount: number;
}

export interface ParticipantLeftEvent {
  type: 'ParticipantLeft';
  userId: string;
  username: string;          // ← NOUVEAU
  participantCount: number;
  reason: string;
}

export interface SessionWelcomeEvent {
  // ... existing ...
  participants: ParticipantInfo[];  // ← NOUVEAU
}
```

### 4.3 `GridPlayerComponent` — Maintenir la liste des participants

```typescript
// Signal pour la liste des participants
readonly participants = signal<ParticipantInfo[]>([]);

// Dans handleEvent:
case 'SessionWelcome':
  this.participants.set(event.participants);
  break;

case 'ParticipantJoined':
  this.participants.update(list => [
    ...list.filter(p => p.userId !== event.userId),
    { userId: event.userId, username: event.username, status: 'JOINED', isCreator: false },
  ]);
  break;

case 'ParticipantLeft':
  this.participants.update(list =>
    list.map(p => p.userId === event.userId ? { ...p, status: 'LEFT' } : p),
  );
  break;
```

### 4.4 Nouveau composant — `ParticipantsPanelComponent`

Un composant shared qui affiche la liste des participants :

```html
<div class="participants-panel">
  <h3>Participants ({{ activeCount() }})</h3>
  @for (p of participants(); track p.userId) {
    <div class="participant" [class.away]="p.status === 'LEFT'">
      <span class="participant__avatar">{{ p.username.charAt(0) }}</span>
      <span class="participant__name">{{ p.username }}</span>
      @if (p.isCreator) {
        <span class="participant__badge">Créateur</span>
      }
      @if (p.status === 'LEFT') {
        <span class="participant__status">Parti</span>
      }
    </div>
  }
</div>
```

---

## 5. Plan d'exécution

| Phase | Fichiers | Description |
|-------|----------|-------------|
| 1 | `Participant.kt`, `SessionLifecycleCommand.kt`, `Session.kt` | Ajouter `username` au domain |
| 2 | `ParticipantDocument`, mapper | Persistence + rétro-compat |
| 3 | `SessionFullDto`, `ParticipantDto`, `SessionMapper` | Nouveau DTO, mapper enrichi |
| 4 | `SessionEvent.*`, `SessionWebSocketController` | Events enrichis avec `username` |
| 5 | `JoinSessionUseCase`, `CreateSessionUseCase` | Passer `username` dans le command |
| 6 | Tests unitaires BFF | `SessionTest`, `JoinSessionUseCaseTest`, `ParticipantsRuleTest` |
| 7 | Tests d'intégration BFF | `SessionWebSocketIT`, `SessionControllerIT` |
| 8 | Angular models | `session.model.ts`, `session-events.model.ts` |
| 9 | `GridPlayerComponent` | Signal `participants`, gestion events |
| 10 | `ParticipantsPanelComponent` | Nouveau composant UI |
| 11 | Tests Jest + E2E | `grid-player.component.spec.ts`, `game-board.spec.ts` |

---

## 6. Impact sur les données existantes

Les `SessionDocument` existants en MongoDB n'ont pas de `username` dans leurs `ParticipantDocument`. Le mapper doit gérer la rétro-compat :

```kotlin
val username = participantDoc.username ?: "Inconnu"
```

Les sessions en cours au moment du déploiement auront des participants affichés comme "Inconnu" — acceptable pour un MVP.

---

## 7. Tests à ajouter

| Test | Type | Vérifie |
|------|------|---------|
| `Session.join()` avec username | TU | Le participant créé a le bon username |
| `Participant.joined()` factory | TU | username stocké |
| `toSessionFullDto()` avec participants | TU | Liste de `ParticipantDto` correcte, `isCreator` correct |
| `ParticipantJoined` event contient username | TU | Broadcast inclut le username |
| Rétro-compat mapper ancien document | IT | `username` null → "Inconnu" |
| `ParticipantsPanelComponent` render | Jest | Affiche les noms, badge créateur, statut LEFT |
| Join → participant visible dans le panel | E2E | Joueur rejoint → son nom apparaît |

