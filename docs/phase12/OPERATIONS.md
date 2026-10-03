# Phase 12: Production Operations Guide

## 1. System Requirements & Provisioning

- **JVM Runtime**: OpenJDK 21 or higher.
- **Git Binary**: Git v2.30+ installed on host / container path.
- **Scratch Directory**: Fast NVMe or tmpfs mount (recommended: `/tmp/secretvault-scratch` or dedicated volume).
- **Concurrency Limits**: Set maximum concurrent repository clones via Spring thread pool (`secretvault.scanner.max-concurrent-scans=8`).

---

## 2. Resource Management

To prevent resource exhaustion during scans of large repositories:
1. **Subprocess Timeout**: Configure default execution timeout (default 60s) via `secretvault.scanner.git-timeout-seconds=60`.
2. **Scratch Storage Auto-Purge**: All temporary clone and extraction directories are wrapped in `try-with-resources` or explicit cleanup hooks in `RepositoryScanService`.
3. **Database Indexing**: The `V18` Flyway migration installs indexes on `(workspace_id, repository_id, status)` and `(fingerprint)` to guarantee sub-millisecond query response on large finding volumes.
