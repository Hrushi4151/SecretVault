# SecretVault SDK — Kubernetes Workload Architecture

## Target Workload Identity Flow

```
Kubernetes Pod (ServiceAccount token projected at /var/run/secrets/...)
       │
       ▼
SecretVault SDK (OidcTokenProvider)
       │
       ▼
Exchanges K8s ServiceAccount OIDC JWT with SecretVault (/api/v1/oidc/auth/exchange)
       │
       ▼
Obtains Ephemeral SecretVault Machine Session (600s TTL)
       │
       ▼
Resolves authorized application secrets directly into JVM memory
```

*(Zero static Kubernetes Secret objects stored in etcd).*
