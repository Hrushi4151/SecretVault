# SecretVault SDK — Troubleshooting Guide

## Common Issues & Resolutions

### 1. `SV_AUTH_REQUIRED` / `AuthenticationException`
- **Cause**: No credentials found in environment or configuration.
- **Fix**: Set `SECRET_VAULT_TOKEN` or configure explicit `CredentialsProvider`.

### 2. `SV_ACCESS_DENIED` / `AuthorizationException`
- **Cause**: The authenticated machine identity lacks `secret.reveal` permission in the target project/environment.
- **Fix**: Add a scoped `MachineAccessGrant` with `secret.reveal` for the target environment.

### 3. `SV_CIRCUIT_OPEN`
- **Cause**: Backend experienced consecutive failures exceeding threshold.
- **Fix**: Verify backend health at `/actuator/health` and check network connectivity.
