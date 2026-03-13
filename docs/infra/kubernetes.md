# Infrastructure — Kubernetes (k3s)

## Pourquoi k3s

k3s est la distribution Kubernetes officielle allégée de Rancher. Elle expose **la même API que k8s standard** (tout ce qu'on apprend s'applique sur GKE/EKS/AKS) mais avec :

- Control plane en binaire unique (~70 MB)
- ~500 MB RAM vs ~2 GB pour kubeadm
- Traefik ingress inclus
- SQLite embarqué (pas besoin d'etcd pour un single-node)

```bash
# Installation
curl -sfL https://get.k3s.io | sh -
```

---

## Namespaces

```
cluster k3s
├── kube-system     ← k3s internals, Traefik
├── cert-manager    ← Let's Encrypt
└── cocro           ← toute l'application
```

---

## Workloads (namespace: cocro)

```
┌────────────────────────────────────────────────────────────────┐
│  namespace: cocro                                              │
│                                                                │
│  ┌──────────────┐   ┌──────────────┐   ┌──────────────────┐  │
│  │  cocro-front │   │   cocro-bff  │   │      minio       │  │
│  │  Deployment  │   │  Deployment  │   │   Deployment     │  │
│  │  nginx:alpine│   │  Spring Boot │   │  minio/minio     │  │
│  │  ~50 MB RAM  │   │  ~512 MB RAM │   │  ~100 MB RAM     │  │
│  │  ClusterIP   │   │  ClusterIP   │   │  ClusterIP       │  │
│  └──────┬───────┘   └──────┬───────┘   └────────┬─────────┘  │
│         │                  │                    │             │
│         └──────────────────┴────────────────────┘             │
│                            │ Ingress                          │
│                                                                │
│  ┌──────────────┐   ┌──────────────┐                          │
│  │   mongodb    │   │    redis     │                          │
│  │  Deployment  │   │  Deployment  │                          │
│  │  mongo:7     │   │  redis:7     │                          │
│  │  ~512 MB RAM │   │  ~128 MB RAM │                          │
│  │  ClusterIP   │   │  ClusterIP   │                          │
│  │  PVC: 10 GB  │   │  PVC: 1 GB  │                          │
│  └──────────────┘   └──────────────┘                          │
│                                                                │
│  ┌────────────────────────────────────────────────────────┐   │
│  │  NetworkPolicy : mongodb + redis                       │   │
│  │  ingress autorisé depuis cocro-bff uniquement          │   │
│  └────────────────────────────────────────────────────────┘   │
└────────────────────────────────────────────────────────────────┘
```

---

## Ingress (Traefik)

```
                    Traefik
                       │
         ┌─────────────┼─────────────┐
         │             │             │
  app.cocro.com  api.cocro.com  storage.cocro.com
         │             │             │
    cocro-front   cocro-bff       minio
    (port 80)     (port 8080)   (port 9000)
                                 bucket: releases
                                 accès: read-only public
```

TLS automatique via cert-manager + Let's Encrypt sur tous les sous-domaines.

---

## Secrets

```yaml
# Secret k8s (ne jamais committer en clair)
apiVersion: v1
kind: Secret
metadata:
  name: cocro-secrets
  namespace: cocro
data:
  MONGO_USER: <base64>
  MONGO_PASSWORD: <base64>
  REDIS_PASSWORD: <base64>
  JWT_SECRET: <base64>
  MINIO_ROOT_USER: <base64>
  MINIO_ROOT_PASSWORD: <base64>
```

---

## PersistentVolumes

| Volume | Taille | Workload | Notes |
|--------|--------|----------|-------|
| mongodb-pvc | 10 GB | MongoDB | Données + index |
| redis-pvc | 1 GB | Redis | AOF persistence |
| minio-pvc | 5 GB | MinIO | APKs + assets |

Total stockage k8s : ~16 GB sur 75 GB disponibles.

---

## Concepts k8s couverts par ce setup

| Concept | Ressource |
|---------|-----------|
| Deployment / ReplicaSet | Tous les workloads |
| Service ClusterIP | Isolation mongo/redis |
| Ingress | Exposition BFF + front + MinIO |
| Secret | Credentials |
| ConfigMap | Variables d'environnement non sensibles |
| PersistentVolume + PVC | Données MongoDB / Redis / MinIO |
| NetworkPolicy | Isolation mongo/redis |
| Namespace | Isolation de l'env cocro |
| TLS / cert-manager | HTTPS automatique |
