# Infrastructure — Vue d'ensemble

## VPS OVH

| Ressource | Valeur |
|-----------|--------|
| CPU | 4 vCores |
| RAM | 8 GB |
| Stockage | 75 GB SSD |
| Réseau | 400 Mbits/s |

### Budget RAM estimé

| Workload | RAM |
|----------|-----|
| OS + système | ~400 MB |
| Minecraft (Podman, hors k3s) | ~2 000 MB |
| k3s control plane | ~500 MB |
| Traefik ingress | ~100 MB |
| cocro-bff (Spring Boot) | ~512 MB |
| MongoDB | ~512 MB |
| Redis | ~128 MB |
| nginx (front Angular) | ~50 MB |
| MinIO (APK storage) | ~100 MB |
| **Total estimé** | **~4 300 MB** |
| **Marge disponible** | **~3 700 MB** |

---

## Architecture générale

```
┌─────────────────────────────────────────────────────────────────┐
│                          VPS OVH                                │
│                                                                 │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │  Podman (hors k3s)                                      │   │
│  │  └── minecraft  :25565 ──────────────────────── public  │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │  k3s                                                    │   │
│  │                                                         │   │
│  │  ┌──────────────────────────────────────────────────┐  │   │
│  │  │  Traefik Ingress  :80 / :443                     │  │   │
│  │  │  ├── app.cocro.com   → cocro-web (nginx)       │  │   │
│  │  │  ├── api.cocro.com   → cocro-bff                 │  │   │
│  │  │  └── storage.cocro.com → minio                   │  │   │
│  │  └──────────────────────────────────────────────────┘  │   │
│  │                                                         │   │
│  │  namespace: cocro                                       │   │
│  │  ├── cocro-bff      ClusterIP  (via Ingress)            │   │
│  │  ├── cocro-web    ClusterIP  (via Ingress)            │   │
│  │  ├── mongodb        ClusterIP  (interne uniquement)     │   │
│  │  ├── redis          ClusterIP  (interne uniquement)     │   │
│  │  └── minio          ClusterIP  (via Ingress, read-only) │   │
│  └─────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
```

---

## Exposition réseau

```
Internet
    │
    ▼
┌───────────────────────────────────┐
│  Firewall OVH                     │
│  :80   ──── ouvert (redirect TLS) │
│  :443  ──── ouvert                │
│  :25565 ─── ouvert (Minecraft)    │
│  tout le reste ──── fermé         │
└───────────────────────────────────┘
    │
    ▼
┌───────────────────────────────────┐
│  Traefik (k3s)                    │
│  + cert-manager + Let's Encrypt   │
│                                   │
│  app.cocro.com     ──► front      │ ✅ public
│  api.cocro.com     ──► bff        │ ✅ public  (auth JWT)
│  storage.cocro.com ──► minio      │ ✅ public  (read-only)
└───────────────────────────────────┘

MongoDB :27017  ──── ClusterIP       🔒 jamais exposé
Redis   :6379   ──── ClusterIP       🔒 jamais exposé
```

---

## Accès dev local à MongoDB et Redis

Depuis ta machine de dev, via SSH tunnel :

```
ta machine                          VPS
─────────                           ───
localhost:27017 ──── SSH tunnel ──► mongo-svc:27017
localhost:6379  ──── SSH tunnel ──► redis-svc:6379
```

```bash
# MongoDB
ssh -L 27017:mongodb.cocro.svc.cluster.local:27017 user@vps -N

# Redis
ssh -L 6379:redis.cocro.svc.cluster.local:6379 user@vps -N
```

---

## Distribution mobile (Android)

Les apps Compose Multiplatform (Android/iOS) ne s'hébergent pas sur le serveur. Elles tournent sur les téléphones des utilisateurs et appellent directement `api.cocro.com`.

```
Téléphone Android
    │
    ├── APK téléchargé depuis storage.cocro.com/releases/cocro.apk
    │
    └── Runtime : appels REST/WebSocket vers api.cocro.com
```

**Distribution APK (hors Play Store) :**
- Build APK via Gradle
- Upload dans MinIO (bucket `releases`, accès public en lecture)
- Lien direct : `https://storage.cocro.com/releases/cocro-android-x.x.x.apk`

**Distribution production :**
- Android → Google Play Store
- iOS → App Store / TestFlight (nécessite Apple Developer 99$/an)
