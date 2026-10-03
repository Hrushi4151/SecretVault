# SecretVault SDK — Container & Docker Integration

## Golden Rules for Containerized Secrets

1. **Zero Bake-In**: Never execute `ENV DB_PASSWORD=...` or `COPY .env` inside a Dockerfile.
2. **Runtime In-Memory Resolution**: The container launches with minimal runtime environment variables configuring SecretVault endpoint and identity (`SECRETVAULT_ENDPOINT`, `SECRETVAULT_WORKSPACE`, `SECRET_VAULT_MACHINE_TOKEN`).
3. **Non-Root Execution**: Container processes run under unprivileged service users (`appuser`).
