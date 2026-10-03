# SecretVault SDK — In-Memory Caching & Tenant Isolation

## Cache Architecture

1. **Multi-Tenant Composite Keys**:
   Format: `workspace::project::environment::secretName::(version|latest)`
   Guarantees that distinct workspaces or environments never collide in memory.

2. **TTL Expiration**:
   Entries are strictly marked expired once their TTL (default 60 seconds) elapses.

3. **Bounded Capacity**:
   LRU eviction policy discards the least recently accessed entries once max capacity (default 500) is exceeded.

4. **Zero Disk Spillage**:
   Cached secrets reside purely on heap and are never persisted to disk or distributed caches.
