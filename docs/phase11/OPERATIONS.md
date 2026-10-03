# SecretVault SDK — Operations & Deployment Guide

## Production Deployment Best Practices

1. **Enforce HTTPS**: Set `allowHttp(false)` in all production environments.
2. **Short Machine Token TTL**: Ensure backend retains the 600-second token lifetime invariant.
3. **Graceful Shutdown**: Implement `@PreDestroy` or `try-with-resources` to cleanly terminate background watchers and thread pools.
4. **Least Privilege Grants**: Grant machine identities access ONLY to specific environment tiers (e.g. `DEVELOPMENT` vs `PRODUCTION`).
