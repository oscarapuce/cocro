# Session & WebSocket — Spec de modifications

> Résultat de l'audit `audit-2026-03-28.md`.
> Ce document décrit les modifications à apporter pour résoudre les incohérences et combler les gaps identifiés.

> **✅ Implémenté le 2026-03-28** — MOD-1 à MOD-9 appliqués. Seul A7 (scheduler de nettoyage sessions expirées) est différé.

---

## Table des matières

1. [Contexte](#1-contexte)
2. [Modifications priorité CRITIQUE (🔴)](#2-modifications-priorité-critique-)
3. [Modifications priorité MOYENNE (🟡)](#3-modifications-priorité-moyenne-)
4. [Modifications documentation (📝)](#4-modifications-documentation-)
5. [Hors périmètre / différé](#5-hors-périmètre--différé)
6. [Ordre d'exécution recommandé](#6-ordre-dexécution-recommandé)

---

## 1. Contexte

L'audit a révélé 9 incohérences entre le code BFF, le code Angular et la documentation. Ce spec regroupe les modifications concrètes à faire, par priorité, avec pour chaque tâche les fichiers impactés et le comportement attendu.

### Périmètre

- **BFF** : `cocro-bff/src/main/kotlin/com/cocro/`
- **Angular** : `cocro-web/src/app/`
- **Docs** : `docs/sessions/`

---

## 2. Modifications priorité CRITIQUE (🔴)

### MOD-1 — Supprimer `SessionConnectEventListener` (code mort + risque double Welcome)

**Réf audit** : INC-2, INC-9

**Problème** : Deux mécanismes envoient `SessionWelcome` — le `SessionConnectEventListener` (via `SessionConnectedEvent` → `/user/queue/session`) et le `@SubscribeMapping` dans `SessionWebSocketController.onWelcomeSubscribe()`. Le client Angular utilise uniquement le pattern `@SubscribeMapping`. Le listener est du code mort qui crée un risque de double Welcome sur `/user/queue/session` (canal écouté par Angular pour `SyncRequired`).

**Fichiers impactés** :
- Supprimer : `cocro-bff/.../presentation/websocket/SessionConnectEventListener.kt`
- Vérifier : `cocro-bff/.../test/.../SessionWebSocketIT.kt` — aucune dépendance sur ce listener

**Comportement attendu** :
- Un seul `SessionWelcome` reçu par le client, via la réponse synchrone du `@SubscribeMapping`.
- Le canal `/user/queue/session` est réservé exclusivement à `SyncRequired`.

**Tests** :
- Les tests d'intégration existants (`SessionWebSocketIT`) doivent passer sans modification (ils utilisent `@SubscribeMapping`).

---

### MOD-2 — Décider de la stratégie heartbeat : implémenter ou documenter l'absence

**Réf audit** : INC-1, RISK-1

**Problème** : La doc mentionne un endpoint `/app/session/{code}/heartbeat` qui n'existe ni côté BFF ni côté Angular. Sans heartbeat applicatif, le `HeartbeatTracker` n'est alimenté qu'au join (markActive) et au disconnect STOMP (markAway). Un client half-open ne sera jamais détecté.

**Option A — Implémenter le heartbeat (recommandé)** :

Fichiers à créer / modifier :

| Fichier | Action |
|---|---|
| `cocro-bff/.../websocket/SessionWebSocketController.kt` | Ajouter `@MessageMapping("/session/{shareCode}/heartbeat")` |
| `cocro-bff/.../port/HeartbeatTracker.kt` | Rien (interface déjà prête : `markActive()`) |
| `cocro-web/.../adapters/session/session-stomp.adapter.ts` | Ajouter `setInterval` envoyant un heartbeat toutes les 20s |
| `cocro-web/.../ports/session/session-socket.port.ts` | (optionnel) Ajouter `startHeartbeat()` / `stopHeartbeat()` |

**BFF — nouveau handler** :
```kotlin
@MessageMapping("/session/{shareCode}/heartbeat")
fun handleHeartbeat(
    @DestinationVariable shareCode: String,
    headerAccessor: SimpMessageHeaderAccessor,
) {
    val auth = headerAccessor.sessionAttributes
        ?.get(StompAuthChannelInterceptor.SESSION_AUTH_KEY) as? CocroAuthentication
        ?: return
    val session = sessionRepository.findByShareCode(SessionShareCode(shareCode)) ?: return
    heartbeatTracker.markActive(session.id, auth.user.userId)
}
```

**Angular — heartbeat périodique** :
```typescript
private heartbeatInterval: ReturnType<typeof setInterval> | null = null;

connect(token, shareCode, onEvent): void {
  // ... existing connect logic ...
  // In onConnect callback, after subscriptions:
  this.heartbeatInterval = setInterval(() => {
    if (this.client?.connected) {
      this.client.publish({ destination: `/app/session/${shareCode}/heartbeat` });
    }
  }, 20_000); // every 20s (< 30s grace period)
}

disconnect(): void {
  if (this.heartbeatInterval) { clearInterval(this.heartbeatInterval); this.heartbeatInterval = null; }
  // ... existing disconnect logic ...
}
```

**Option B — Documenter l'absence et accepter la limite** :

Si le heartbeat n'est pas implémenté, documenter clairement dans `lifecycle.md` et `session-flow.html` que la détection de perte de connexion repose uniquement sur le STOMP disconnect TCP, et que les half-open connections ne sont pas détectées.

**Recommandation** : Option A. Le code est simple, le `HeartbeatTracker` est déjà prêt. L'ajout est minimal.

---

## 3. Modifications priorité MOYENNE (🟡)

### MOD-3 — Ajouter les types manquants dans `subscribeTopic()` (Angular)

**Réf audit** : INC-3

**Fichier** : `cocro-web/.../adapters/session/session-stomp.adapter.ts`

**Modification** :
```typescript
// Ligne ~89 — ajouter SessionEndedEvent et SessionInterruptedEvent au type union
const event = JSON.parse(msg.body) as
  | ParticipantJoinedEvent
  | ParticipantLeftEvent
  | GridUpdatedEvent
  | GridCheckedEvent
  | SessionEndedEvent       // ← AJOUT
  | SessionInterruptedEvent; // ← AJOUT
```

Ajouter les imports correspondants.

---

### MOD-4 — Ajouter `isCorrect` à `SessionEvent.GridChecked`

**Réf audit** : INC-5

Le broadcast `GridChecked` n'inclut pas `isCorrect`, ce qui empêche les autres participants de savoir si la grille est correcte avant de recevoir un éventuel `SessionEnded`.

**Fichiers** :

| Fichier | Modification |
|---|---|
| `cocro-bff/.../dto/notification/SessionEvent.kt` | Ajouter `val isCorrect: Boolean` à `GridChecked` |
| `cocro-bff/.../usecase/CheckGridUseCase.kt` | Passer `isCorrect = result.isCorrect` dans le broadcast |
| `cocro-web/.../models/session-events.model.ts` | Ajouter `isCorrect: boolean` à `GridCheckedEvent` |

---

### MOD-5 — Appeler `leaveSession` dans `ngOnDestroy`

**Réf audit** : INC-6

**Fichier** : `cocro-web/.../features/grid/play/grid-player.component.ts`

**Modification** :
```typescript
ngOnDestroy(): void {
  // Leave the session properly (fire-and-forget)
  if (this.shareCode()) {
    this.leaveSession.execute(this.shareCode()).subscribe({ error: () => {} });
  }
  this.sessionSocket.disconnect();
  this.selector.initGrid(createEmptyGrid('0', '', 10, 10));
  this.letterAuthors.clearAll();
}
```

**Note** : Le `POST /leave` est fire-and-forget. Si la requête échoue (navigateur en cours de fermeture), le heartbeat timeout prendra le relais.

---

### MOD-6 — Ajouter `beforeunload` + `canDeactivate` guard

**Réf audit** : INC-7

**Fichiers** :

| Fichier | Modification |
|---|---|
| `cocro-web/.../grid/play/grid-player.component.ts` | Ajouter `@HostListener('window:beforeunload')` |
| `cocro-web/.../grid/play/play.routes.ts` | Ajouter un `canDeactivate` guard |

**`beforeunload`** (fermeture onglet / navigation externe) :
```typescript
@HostListener('window:beforeunload')
onBeforeUnload(): void {
  if (this.shareCode() && this.connected()) {
    // Beacon API for reliable leave on tab close
    const url = `${environment.apiBaseUrl}/api/sessions/leave`;
    const body = JSON.stringify({ shareCode: this.shareCode() });
    navigator.sendBeacon(url, new Blob([body], { type: 'application/json' }));
  }
}
```

**Note** : `sendBeacon` ne transporte pas le JWT. Pour que ça fonctionne, il faudrait soit :
- Un token temporaire dans un cookie HttpOnly (changement d'architecture d'auth)
- Accepter que `beforeunload` ne fait que le `disconnect()` STOMP et le heartbeat timeout se charge du reste

**Recommandation pragmatique** : Ne pas implémenter `sendBeacon` (problème JWT). S'appuyer sur le disconnect STOMP + heartbeat timeout pour la fermeture d'onglet. Ajouter uniquement le `canDeactivate` guard pour la navigation intra-app :

```typescript
// play-leave.guard.ts
export const playLeaveGuard: CanDeactivateFn<GridPlayerComponent> = (component) => {
  if (component.connected()) {
    component.leave();
  }
  return true;
};
```

---

### MOD-7 — Aligner `GridCheckResponse` Angular avec `GridCheckSuccess` BFF

**Réf audit** : INC-4

**Fichier** : `cocro-web/.../models/session.model.ts`

```typescript
export interface GridCheckResponse {
  shareCode: string;      // ← AJOUT
  isComplete: boolean;
  isCorrect: boolean;
  correctCount: number;
  totalCount: number;
  filledCount: number;    // ← AJOUT
  wrongCount: number;     // ← AJOUT
}
```

Impact : la grille de jeu pourrait afficher des informations plus riches (nombre de cases fausses, rempli vs total).

---

## 4. Modifications documentation (📝)

### MOD-8 — Mises à jour déjà appliquées

Les corrections doc suivantes ont été appliquées lors de l'audit :

| Document | Correction |
|---|---|
| `lifecycle.md` | `SyncRequired` : "defined but not yet sent" → description correcte (envoyé sur CAS conflict) |
| `lifecycle.md` | Heartbeat section : réécriture — endpoint non implémenté, STOMP disconnect only |
| `lifecycle.md` | `SessionWelcome` topic : `/user/queue/session` → `/app/session/{code}/welcome` (`@SubscribeMapping`) |
| `session-ddd.md` | `kernel` package → `domain` (commit e60c60c) |
| `session-ddd.md` | CAS conflict : "currently raises an exception" → description du flow complet |
| `session-flow.html` | §3 : heartbeat barré + warning ⚠️ NON IMPLÉMENTÉ |
| `session-flow.html` | §4 : `GridChecked` payload corrigé (`userId` ajouté, `isCorrect` retiré) |
| `session-flow.html` | §5 : heartbeat section mise à jour avec warning |

### MOD-9 — Post-implémentation

Après chaque MOD-1 à MOD-7, mettre à jour :
- `lifecycle.md` — si le heartbeat est implémenté (MOD-2 Option A), rétablir la section heartbeat
- `session-flow.html` — retirer les warnings ⚠️ une fois le heartbeat implémenté
- `audit-2026-03-28.md` — marquer les incohérences comme résolues

---

## 5. Hors périmètre / différé

| Sujet | Raison |
|---|---|
| Nettoyage des sessions INTERRUPTED/ENDED (RISK-2) | Scheduler de TTL à planifier séparément — impact MongoDB + Redis |
| Retry pour `SyncRequired` manqué (RISK-3) | Nécessite un mécanisme de polling de revision ou de réconciliation périodique — design plus lourd |
| Suppression de `POST /api/sessions/start` dans la config de sécurité (INC-8) | Vestige — vérifier qu'il n'est référencé nulle part puis retirer de `SecurityFilterChain` |
| `GET /api/sessions/{code}/state` inutilisé côté Angular | Le port est défini (`getState()`), l'adapter est implémenté, mais aucun use case Angular ne l'appelle. À garder pour un usage futur ou supprimer si le sync est suffisant. |

---

## 6. Ordre d'exécution recommandé

Les modifications sont ordonnées pour minimiser les conflits et maximiser la valeur immédiate.

```
Phase 1 — Nettoyage (pas de nouvelle feature, réduction du risque)
  ├── MOD-1  Supprimer SessionConnectEventListener          [BFF]   ~15 min
  ├── MOD-3  Types manquants dans subscribeTopic()           [Angular] ~5 min
  └── MOD-7  Aligner GridCheckResponse                       [Angular] ~5 min

Phase 2 — Robustesse session
  ├── MOD-4  Ajouter isCorrect à GridChecked                [BFF+Angular] ~15 min
  ├── MOD-5  leaveSession dans ngOnDestroy                  [Angular] ~10 min
  └── MOD-6  canDeactivate guard sur route /play            [Angular] ~15 min

Phase 3 — Heartbeat applicatif
  └── MOD-2  Endpoint WS + heartbeat périodique Angular     [BFF+Angular] ~45 min
             (inclut tests d'intégration)

Phase 4 — Documentation post-implémentation
  └── MOD-9  Mise à jour docs lifecycle, flow, audit        [Docs] ~15 min
```

**Effort total estimé** : ~2h

---

## Annexe — Récapitulatif des fichiers impactés

| Fichier | MOD |
|---|---|
| **Supprimer** `cocro-bff/.../websocket/SessionConnectEventListener.kt` | MOD-1 |
| `cocro-bff/.../websocket/SessionWebSocketController.kt` | MOD-2 |
| `cocro-bff/.../dto/notification/SessionEvent.kt` | MOD-4 |
| `cocro-bff/.../usecase/CheckGridUseCase.kt` | MOD-4 |
| `cocro-web/.../adapters/session/session-stomp.adapter.ts` | MOD-2, MOD-3 |
| `cocro-web/.../ports/session/session-socket.port.ts` | MOD-2 (opt.) |
| `cocro-web/.../models/session-events.model.ts` | MOD-3, MOD-4 |
| `cocro-web/.../models/session.model.ts` | MOD-7 |
| `cocro-web/.../features/grid/play/grid-player.component.ts` | MOD-5, MOD-6 |
| `cocro-web/.../features/grid/play/play.routes.ts` | MOD-6 |
| **Créer** `cocro-web/.../guards/play-leave.guard.ts` | MOD-6 |
| `docs/sessions/lifecycle.md` | MOD-8 ✅, MOD-9 |
| `docs/sessions/session-ddd.md` | MOD-8 ✅ |
| `docs/sessions/session-flow.html` | MOD-8 ✅, MOD-9 |
| `docs/sessions/audit-2026-03-28.md` | MOD-9 |

