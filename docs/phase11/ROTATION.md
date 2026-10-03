# SecretVault SDK — Dynamic Secret Rotation & Watchers

## Secret Rotation Lifecycle

```
Secret v1 in Cache
        │
        ▼ (Secret rotated in SecretVault backend -> v2 created)
SDK Background Watcher polls /api/v1/.../secrets metadata
        │
        ▼ (Current version mismatch detected)
SDK fetches & decrypts v2 atomically
        │
        ▼
Cache updated to v2 (v1 evicted)
        │
        ▼
Fires SecretRefreshListener callbacks on active Spring beans (@SecretVaultValue)
```

Applications update secrets live without restarting JVM instances or dropping network connections.
