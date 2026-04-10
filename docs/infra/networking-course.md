# Cours — Réseau, Reverse-Proxy & Service Mesh

> Comment une requête arrive depuis `cocro.fr` jusqu'à ton app Angular,
> ton BFF Spring Boot, et comment administrer MongoDB/Redis à distance.

---

## 1. Le parcours d'une requête web (Vue d'ensemble)

```
 Utilisateur (navigateur)
       │
       │  https://cocro.fr
       ▼
 ┌──────────────┐
 │  DNS (OVH)   │   cocro.fr  →  151.x.x.x  (IP du VPS)
 └──────┬───────┘
        │
        ▼
 ┌──────────────────────────────────────────────────────────┐
 │                    VPS OVH  (151.x.x.x)                  │
 │                                                          │
 │   :80/:443  ──►  Traefik  (reverse-proxy / Ingress)      │
 │                     │                                     │
 │          ┌──────────┼──────────┐                          │
 │          │          │          │                           │
 │    cocro.fr    api.cocro.fr  storage.cocro.fr             │
 │          │          │          │                           │
 │     ┌────▼───┐  ┌───▼────┐  ┌─▼─────┐                    │
 │     │ nginx  │  │  BFF   │  │ MinIO │                    │
 │     │ (SPA)  │  │ Spring │  │       │                    │
 │     └────────┘  └───┬────┘  └───────┘                    │
 │                     │                                     │
 │              ┌──────┴──────┐                              │
 │              │             │                               │
 │         ┌────▼───┐   ┌────▼───┐                           │
 │         │MongoDB │   │ Redis  │   ← ClusterIP interne     │
 │         │ :27017 │   │ :6379  │   🔒 pas exposés          │
 │         └────────┘   └────────┘                           │
 └──────────────────────────────────────────────────────────┘
```

**Réponse à ta question :** Oui, quand un user tape `cocro.fr` :
1. Le navigateur résout le DNS → obtient l'IP du VPS
2. La requête HTTPS arrive sur le port 443 du VPS
3. Traefik la route vers **nginx** qui sert les fichiers statiques Angular (HTML/CSS/JS)
4. Le navigateur **télécharge** le bundle Angular → l'app tourne **côté client**
5. L'app Angular fait des appels XHR/WebSocket vers `api.cocro.fr` → Traefik route vers le BFF

---

## 2. Nginx — Serveur web / Reverse-proxy classique

### C'est quoi ?

Nginx est un **serveur web** et **reverse-proxy** ultra-performant. Dans notre stack, il a **un seul rôle** :

> **Servir les fichiers statiques** de l'app Angular (le build `ng build` produit du HTML/CSS/JS).

```
                  nginx (dans k3s)
                       │
        ┌──────────────┼──────────────┐
        │              │              │
   index.html    main.js        styles.css
   (200 KB)     (800 KB)        (50 KB)

   → Le navigateur télécharge tout ça une seule fois
   → Ensuite l'app Angular tourne 100% côté client
   → Les appels API vont vers api.cocro.fr (pas nginx)
```

### Nginx comme reverse-proxy (HORS notre cas)

Nginx peut aussi être un reverse-proxy (comme Traefik). C'est un choix **OU l'un OU l'autre** pour le routing HTTP :

```
   Internet ──► Nginx (reverse-proxy) ──► backend1, backend2...
                  OU
   Internet ──► Traefik (reverse-proxy) ──► backend1, backend2...
```

### Résumé du rôle Nginx chez nous

| Rôle | Utilisé ? |
|------|-----------|
| Servir les fichiers statiques Angular | ✅ OUI |
| Reverse-proxy / routing HTTP | ❌ NON (c'est Traefik) |
| TLS termination | ❌ NON (c'est Traefik) |

---

## 3. Traefik — Reverse-proxy / Ingress Controller

### C'est quoi ?

Traefik est un **reverse-proxy dynamique** conçu pour le cloud-native. Il est le **Ingress Controller** par défaut de k3s.

### Son rôle

```
Internet (:443)
     │
     ▼
┌─────────────────────────────────────────────────┐
│              TRAEFIK                              │
│                                                   │
│  🔒 TLS termination (Let's Encrypt automatique)  │
│                                                   │
│  Règles de routage (lues depuis les Ingress k8s): │
│                                                   │
│  Host: cocro.fr         ──►  nginx:80     (SPA)  │
│  Host: api.cocro.fr     ──►  cocro-bff:8080      │
│  Host: storage.cocro.fr ──►  minio:9000          │
│                                                   │
│  + middleware : rate-limit, headers, redirect...  │
└─────────────────────────────────────────────────┘
```

### Pourquoi Traefik et pas Nginx en reverse-proxy ?

| Critère | Traefik | Nginx (reverse-proxy) |
|---------|---------|----------------------|
| Configuration | **Dynamique** — lit les Ingress k8s automatiquement | Statique — fichiers `.conf` à recharger |
| TLS (Let's Encrypt) | **Intégré** nativement | Besoin de certbot/cert-manager séparé |
| Découverte de services | **Automatique** (k8s, Docker, Consul…) | Manuelle |
| Dashboard | ✅ Intégré | ❌ |
| Perf brutes | Bon | **Meilleur** (C vs Go) |
| Cas d'usage idéal | Kubernetes, microservices | Servir des fichiers, charge très élevée |

**Conclusion : on utilise les DEUX, mais pour des rôles différents :**
- **Traefik** = reverse-proxy / routeur / TLS (couche "entrée")
- **Nginx** = serveur de fichiers statiques Angular (couche "service")

---

## 4. Ingress (concept Kubernetes)

### C'est quoi ?

**Ingress** est une **ressource Kubernetes** (un objet YAML). Ce n'est PAS un logiciel. C'est une **déclaration** de règles de routage HTTP.

```yaml
# Exemple : Ingress pour cocro
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: cocro-ingress
  namespace: cocro
  annotations:
    cert-manager.io/cluster-issuer: letsencrypt-prod
spec:
  tls:
    - hosts: [cocro.fr, api.cocro.fr]
      secretName: cocro-tls
  rules:
    - host: cocro.fr
      http:
        paths:
          - path: /
            pathType: Prefix
            backend:
              service:
                name: cocro-web
                port: { number: 80 }
    - host: api.cocro.fr
      http:
        paths:
          - path: /
            pathType: Prefix
            backend:
              service:
                name: cocro-bff
                port: { number: 8080 }
```

### Ingress vs Ingress Controller

```
┌─────────────────────────────────────────────────────┐
│                                                       │
│   Ingress (YAML)           Ingress Controller         │
│   ═══════════════          ══════════════════          │
│   "Quoi router"     →     "Qui exécute le routing"   │
│                                                       │
│   Déclaratif               Logiciel réel              │
│   (ressource k8s)          (pod qui tourne)           │
│                                                       │
│   Exemples :               Exemples :                 │
│   rules, host, path        • Traefik  ← nous         │
│                             • Nginx Ingress Controller│
│                             • HAProxy                 │
│                             • Envoy / Istio Gateway   │
└─────────────────────────────────────────────────────┘
```

**Analogie :** L'Ingress c'est le **plan** de la maison. L'Ingress Controller c'est le **maçon** qui le construit.

---

## 5. Istio — Service Mesh (avancé)

### C'est quoi ?

Istio est un **service mesh** : une couche réseau **entre les pods** qui gère le trafic **interne** au cluster (est-ouest), pas le trafic entrant (nord-sud).

```
                        SANS Service Mesh
                        ═════════════════
    ┌─────────┐  HTTP direct  ┌─────────┐  HTTP direct  ┌─────────┐
    │   BFF   │──────────────►│ Service │──────────────►│  Redis  │
    │         │               │    B    │               │         │
    └─────────┘               └─────────┘               └─────────┘


                        AVEC Istio (Service Mesh)
                        ═════════════════════════
    ┌──────────────┐          ┌──────────────┐          ┌──────────────┐
    │  BFF         │          │  Service B   │          │  Redis       │
    │  ┌────────┐  │  mTLS    │  ┌────────┐  │  mTLS    │  ┌────────┐  │
    │  │ Envoy  │──┼─────────►│  │ Envoy  │──┼─────────►│  │ Envoy  │  │
    │  │ sidecar│  │ chiffré  │  │ sidecar│  │ chiffré  │  │ sidecar│  │
    │  └────────┘  │          │  └────────┘  │          │  └────────┘  │
    └──────────────┘          └──────────────┘          └──────────────┘

    Chaque pod a un proxy Envoy injecté automatiquement ("sidecar").
    Tout le trafic inter-services passe par Envoy.
```

### Ce qu'Istio apporte

| Feature | Description |
|---------|-------------|
| **mTLS automatique** | Chiffrement entre tous les services, sans changer le code |
| **Observabilité** | Métriques, traces distribuées (Jaeger), graphes de trafic (Kiali) |
| **Traffic management** | Canary deploy, A/B testing, circuit breaker, retry |
| **Policies** | Rate-limit par service, authorization policies |

### Istio Gateway vs Ingress

Istio a son propre concept de "Gateway" qui remplace l'Ingress classique :

```
   Ingress classique (Traefik)          Istio Gateway
   ════════════════════════════          ═══════════════
   Internet → Traefik → Service         Internet → Istio Gateway → VirtualService → Service
                                                    (Envoy)
```

### On en a besoin pour cocro ?

**NON.** Istio est conçu pour les architectures à **dizaines de microservices** (Netflix, Google). Pour cocro (3-4 services), c'est du **over-engineering** qui consommerait ~500 MB de RAM en plus.

---

## 6. Résumé comparatif complet

```
┌──────────────────────────────────────────────────────────────────────┐
│                                                                      │
│   COUCHE           │  Outil        │  Trafic      │  Rôle            │
│   ═════════════════╪═══════════════╪══════════════╪═════════════════ │
│   Entrée (Nord→Sud)│  Traefik      │  Internet→k8s│  Reverse-proxy,  │
│                    │  (Ingress     │              │  TLS, routing    │
│                    │   Controller) │              │  par Host/Path   │
│   ─────────────────┼───────────────┼──────────────┼───────────────── │
│   Ingress (k8s)    │  (YAML)       │  —           │  Déclaration     │
│                    │               │              │  des règles      │
│   ─────────────────┼───────────────┼──────────────┼───────────────── │
│   Serveur web      │  Nginx        │  Traefik→Pod │  Servir HTML/    │
│                    │               │              │  CSS/JS Angular  │
│   ─────────────────┼───────────────┼──────────────┼───────────────── │
│   Mesh (Est↔Ouest) │  Istio        │  Pod↔Pod     │  mTLS, traces,  │
│   (pas utilisé)    │  (Envoy       │  interne     │  canary, policy  │
│                    │   sidecars)   │              │                  │
└──────────────────────────────────────────────────────────────────────┘
```

---

## 7. Schéma complet de l'architecture cocro

```
                              Internet
                                 │
                    ┌────────────┼────────────┐
                    │            │            │
               cocro.fr    api.cocro.fr   storage.cocro.fr
                    │            │            │
                    ▼            ▼            ▼
┌───────────────────────────────────────────────────────────────────┐
│  VPS OVH (151.x.x.x)                                             │
│                                                                   │
│  ┌─────────────────────────────────────────────────────────────┐ │
│  │  k3s                                                        │ │
│  │                                                             │ │
│  │  ┌───────────────────────────────────────────────────────┐  │ │
│  │  │  Traefik  :80 → redirect :443                        │  │ │
│  │  │           :443 → TLS termination (Let's Encrypt)     │  │ │
│  │  │                                                       │  │ │
│  │  │  ┌──────────┐  ┌──────────┐  ┌──────────┐           │  │ │
│  │  │  │ cocro.fr │  │api.cocro │  │storage.  │           │  │ │
│  │  │  │    │     │  │  .fr │   │  │cocro.fr│           │  │ │
│  │  │  └────┼─────┘  └─────┼────┘  └────┼─────┘           │  │ │
│  │  └───────┼──────────────┼────────────┼───────────────────┘  │ │
│  │          │              │            │                       │ │
│  │     ┌────▼────┐   ┌────▼────┐  ┌────▼────┐                 │ │
│  │     │  nginx  │   │cocro-bff│  │  MinIO  │                 │ │
│  │     │  (SPA)  │   │ Spring  │  │         │                 │ │
│  │     │         │   │  Boot   │  │         │                 │ │
│  │     └─────────┘   └────┬────┘  └─────────┘                 │ │
│  │                        │                                    │ │
│  │                 ┌──────┴──────┐                              │ │
│  │                 │             │                               │ │
│  │           ┌─────▼─────┐ ┌────▼─────┐                        │ │
│  │           │  MongoDB  │ │  Redis   │  ← ClusterIP           │ │
│  │           │  :27017   │ │  :6379   │  🔒 interne seul.     │ │
│  │           └───────────┘ └──────────┘                        │ │
│  │                                                             │ │
│  └─────────────────────────────────────────────────────────────┘ │
│                                                                   │
│  ┌──────────────────────────────┐                                │
│  │  Podman (hors k3s)           │                                │
│  │  └── minecraft  :25565       │                                │
│  └──────────────────────────────┘                                │
│                                                                   │
│  SSH :22 ─── pour admin + tunnels vers Mongo/Redis               │
│                                                                   │
└───────────────────────────────────────────────────────────────────┘
```

---

## 8. Admin MongoDB & Redis depuis le VPS

MongoDB et Redis ne sont **jamais** exposés directement sur Internet (pas de port public). L'accès admin se fait via **SSH tunnel** depuis ta machine locale.

### Méthode : SSH port-forward

```
 Ta machine (macOS)                         VPS OVH
 ══════════════════                         ════════
                        SSH tunnel
 localhost:27017  ◄══════════════════►  mongodb.cocro.svc:27017
 localhost:6379   ◄══════════════════►  redis.cocro.svc:6379

 → Tu ouvres MongoDB Compass sur localhost:27017
 → Tu ouvres RedisInsight sur localhost:6379
 → Le trafic passe chiffré dans le tunnel SSH
 → Aucun port Mongo/Redis n'est ouvert sur Internet
```

### Commandes

```bash
# Terminal 1 — Tunnel MongoDB
ssh -L 27017:mongodb.cocro.svc.cluster.local:27017 user@vps-ip -N

# Terminal 2 — Tunnel Redis
ssh -L 6379:redis.cocro.svc.cluster.local:6379 user@vps-ip -N

# Ensuite :
# MongoDB Compass → mongodb://admin:password@localhost:27017
# redis-cli → redis-cli -h localhost -p 6379 -a redispass
```

### Alternative : kubectl port-forward

Si tu as `kubectl` configuré en local avec le kubeconfig du VPS :

```bash
# MongoDB
kubectl port-forward -n cocro svc/mongodb 27017:27017

# Redis
kubectl port-forward -n cocro svc/redis 6379:6379
```

### Schéma de sécurité

```
Internet                                    VPS
════════                                    ═══

❌ Port 27017 fermé sur le firewall OVH
❌ Port 6379 fermé sur le firewall OVH

✅ Port 22 (SSH) ouvert mais :
   └── Auth par clé SSH uniquement (pas de password)
   └── fail2ban actif
   └── Tunnel vers services internes k8s uniquement
```

---

## 9. Glossaire rapide

| Terme | Définition |
|-------|------------|
| **DNS** | Traduit `cocro.fr` → adresse IP du VPS |
| **Reverse-proxy** | Reçoit les requêtes et les redirige vers le bon service interne |
| **Ingress** | Ressource k8s YAML qui déclare les règles de routage |
| **Ingress Controller** | Le logiciel qui exécute ces règles (Traefik, Nginx IC…) |
| **TLS termination** | Déchiffrer le HTTPS à l'entrée, puis parler HTTP en interne |
| **Service Mesh** | Couche réseau entre pods pour mTLS, observabilité, traffic mgmt |
| **ClusterIP** | Service k8s accessible uniquement depuis l'intérieur du cluster |
| **NodePort** | Service k8s exposé sur un port du nœud (pas utilisé chez nous) |
| **SSH tunnel** | Canal chiffré pour accéder à un port distant comme s'il était local |
| **Sidecar** | Container injecté à côté de ton app dans le même pod (pattern Istio) |
| **mTLS** | TLS mutuel — les deux parties s'authentifient (pas juste le serveur) |
| **Envoy** | Proxy L4/L7 haute perf utilisé par Istio comme sidecar |

