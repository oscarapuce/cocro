# Roadmap — Multi-Session Heartbeat Fix

**Date :** 2026-04-01
**Priorité :** v0.2.0
**Risque actuel :** Moyen (le reverse lookup `user:{userId}:session` est un STRING → un seul sessionId par user)

---

## 1. Problème

### 1.1 Bug : reverse lookup mono-session

Le `RedisHeartbeatTracker` stocke le mapping user→session comme un simple `STRING` :

```
user:{userId}:session → "sessionId"     ← une seule valeur
```

Si un user est participant dans session-A et rejoint session-B :
1. `registerUserSession(userId, session-B)` **écrase** session-A
2. À la déconnexion STOMP, `getSessionIdForUser(userId)` renvoie session-B
3. `markAway` est appelé sur la **mauvaise session**
4. Session-A ne sait jamais que le user est parti → participant fantôme

### 1.2 Comportement quand la session est ENDED

Quand une session passe en `ENDED` :
- Le `HeartbeatTimeoutScheduler` ne la surveille plus (elle est retirée de `sessions:active` via `deactivate()`)
- `Session.leave()` est un `data class copy()` pur — il marque le participant `LEFT` sans vérifier le status
- Mais `applyLeave()` (via le lifecycle command) vérifie que le participant est `JOINED`
- **Pas de nettoyage automatique** des clés Redis heartbeat quand une session passe en ENDED → clés orphelines avec TTL 24h

### 1.3 Scénario : user passe de session A à session B sans finir A

```
1. Oscar joue dans session-A (PLAYING)
   Redis: user:oscar:session → "session-A"
   
2. Oscar ouvre session-B dans un nouvel onglet → JoinSessionUseCase
   Redis: user:oscar:session → "session-B"  ← session-A perdue
   
3. Oscar ferme l'onglet de session-A
   → StompSessionEventListener.onDisconnect()
   → getSessionIdForUser(oscar) → "session-B"  ← FAUX
   → markAway(session-B, oscar) au lieu de session-A
   
4. Résultat :
   - session-A : Oscar apparaît toujours connecté (fantôme)
   - session-B : Oscar est marqué "away" alors qu'il est toujours là
   - 30s plus tard : HeartbeatTimeoutScheduler éjecte Oscar de session-B
```

---

## 2. Solution proposée

### 2.1 Remplacer le reverse lookup STRING par un mécanisme basé sur la connexion WebSocket

**Approche retenue : utiliser le `shareCode` stocké dans les session attributes WebSocket.**

Le `StompAuthChannelInterceptor` stocke déjà le `shareCode` dans les session attributes lors du CONNECT :

```kotlin
// StompAuthChannelInterceptor.kt — déjà implémenté
accessor.getFirstNativeHeader("shareCode")
    ?.let { attrs[SESSION_SHARE_CODE_KEY] = it }
```

Au lieu de faire un reverse lookup Redis (`user → session`), on peut récupérer le `shareCode` directement depuis les session attributes WebSocket à la déconnexion.

### 2.2 Changements BFF

#### 2.2.1 `StompSessionEventListener` — utiliser les session attributes

```kotlin
// AVANT
val sessionId = heartbeatTracker.getSessionIdForUser(userId)

// APRÈS
val shareCode = event.message.headers
    .get(SimpMessageHeaderAccessor.SESSION_ATTRIBUTES)
    ?.get(StompAuthChannelInterceptor.SESSION_SHARE_CODE_KEY) as? String
val session = shareCode?.let { sessionRepository.findByShareCode(SessionShareCode(it)) }
val sessionId = session?.id
```

Chaque connexion WebSocket porte **son propre** shareCode → pas de conflit multi-session.

#### 2.2.2 `HeartbeatTracker` — supprimer le reverse lookup

Méthodes à supprimer du port et de l'implémentation Redis :
- `registerUserSession(userId, sessionId)`
- `unregisterUserSession(userId)`
- `getSessionIdForUser(userId)`
- Clé Redis `user:{userId}:session` → plus nécessaire

Nettoyage des appelants :
- `JoinSessionUseCase` : supprimer `heartbeatTracker.registerUserSession(...)`
- `CreateSessionUseCase` : supprimer `heartbeatTracker.registerUserSession(...)`
- `LeaveSessionUseCase` : supprimer `heartbeatTracker.unregisterUserSession(...)`
- `HeartbeatTimeoutScheduler` : supprimer `heartbeatTracker.unregisterUserSession(...)`

#### 2.2.3 Nettoyage heartbeat à la transition ENDED

Dans `CheckGridUseCase`, quand la session passe en `ENDED` :
```kotlin
if (isComplete && isCorrect) {
    // ... existing end() logic ...
    // Cleanup heartbeat keys for all participants
    session.participants.forEach { p ->
        heartbeatTracker.remove(session.id, p.userId)
    }
    sessionGridStateCache.deactivate(session.id)
}
```

### 2.3 Changements Angular

Aucun changement côté Angular — le client envoie déjà le `shareCode` dans les headers STOMP CONNECT.

### 2.4 Changements Redis

Clés **supprimées** :
```
user:{userId}:session    ← supprimé (plus de reverse lookup)
```

Clés **inchangées** :
```
session:{id}:heartbeat:active
session:{id}:heartbeat:away
session:{id}:state
session:{id}:lastFlush
sessions:active
```

---

## 3. Plan d'exécution

| Étape | Fichier(s) | Description |
|-------|-----------|-------------|
| 1 | `StompSessionEventListener.kt` | Récupérer le shareCode depuis les session attributes au lieu du reverse lookup Redis |
| 2 | `HeartbeatTracker.kt` (port) | Supprimer `registerUserSession`, `unregisterUserSession`, `getSessionIdForUser` |
| 3 | `RedisHeartbeatTracker.kt` | Supprimer l'implémentation + clé `user:{userId}:session` |
| 4 | `InMemoryHeartbeatTracker.kt` | Supprimer l'implémentation in-memory |
| 5 | `JoinSessionUseCase.kt` | Supprimer appel `registerUserSession` |
| 6 | `CreateSessionUseCase.kt` | Supprimer appel `registerUserSession` |
| 7 | `LeaveSessionUseCase.kt` | Supprimer appel `unregisterUserSession` |
| 8 | `HeartbeatTimeoutScheduler.kt` | Supprimer appel `unregisterUserSession` |
| 9 | `CheckGridUseCase.kt` | Ajouter nettoyage heartbeat à la transition ENDED |
| 10 | Tests unitaires | Mettre à jour `JoinSessionUseCaseTest`, `LeaveSessionUseCaseTest`, `HeartbeatTimeoutSchedulerTest` |
| 11 | Tests d'intégration | Mettre à jour `SessionWebSocketIT` — scénario multi-onglet |
| 12 | `cache.md` | Mettre à jour la doc Redis (supprimer `user:{userId}:session`) |

---

## 4. Tests à ajouter

| Test | Type | Description |
|------|------|-------------|
| User dans 2 sessions, déconnecte la 1ère | IT (WebSocket) | Vérifier que seule la bonne session reçoit `markAway` |
| Session ENDED → clés heartbeat nettoyées | TU | Vérifier que `remove()` est appelé pour tous les participants |
| Déconnexion sans shareCode dans attributes | TU | Vérifier que le handler ne crash pas (log + skip) |
| Grace period multi-session | IT | User away dans session-A, actif dans session-B → seul A est impacté |

---

## 5. Risques et edge cases

| Edge case | Comportement attendu |
|-----------|---------------------|
| User ouvre 2 onglets sur la même session | 2 connexions WebSocket, même shareCode → 2 STOMP DISCONNECT → 1 seul `markAway` (idempotent) |
| User dans session A (PLAYING) + session B (ENDED) | Session B n'a plus de clés heartbeat (nettoyées à ENDED) → pas de conflit |
| User ferme le navigateur (toutes les connexions) | Chaque onglet fire un DISCONNECT indépendant → chaque session est notifiée |
| Redis restart (clés perdues) | `HeartbeatTimeoutScheduler` ne trouve rien dans `away` → pas d'éviction erronée. Les users devront re-join |

