# SecretVault CLI — Troubleshooting & Diagnostics

When encountering issues with the SecretVault CLI, follow this step-by-step diagnostic guide.

---

## 1. Run Diagnostic Health Check (`secretvault doctor`)

The fastest way to pinpoint connectivity, authentication, and configuration problems:

```bash
secretvault doctor --verbose
```

### Healthy Diagnostic Output Example:
```
SecretVault Doctor — Diagnostic Health Check

✓ Java Runtime: 24.0.1 (Mac OS X (aarch64))
✓ Configuration Storage: /Users/nimisha/Library/Application Support/SecretVault
✓ Encrypted Credential Store: Operational (AES-256-GCM)
✓ Server Reachable: http://localhost:8080 [Status: UP, Latency: 129ms, Version: 0.1.0-SNAPSHOT]
✓ Authentication: Valid session for developer@secretvault.io (Profile: default)
✓ Active Context: Workspace [default] -> Project [payment-gateway] -> Environment [development]

Doctor check passed! CLI is ready for development.
```

---

## 2. Common Errors and Resolutions

### `ERROR: Invalid email or password`
- Verify that your user account has been registered on the server.
- Verify that you are connecting to the correct server URL (`secretvault auth status`).

---

### `ERROR: Not authenticated. Please run 'secretvault auth login' first.`
- Your active profile has not authenticated, or stored credentials were cleared.
- Re-run `secretvault auth login --email <email>`.

---

### `ERROR: Session expired and token refresh failed.`
- The refresh token has expired or was revoked by a security administrator on the server.
- Log in again with `secretvault auth login`.

---

### `ERROR: Workspace context is missing.`
- The target workspace is not specified.
- Pass `--workspace <name>` or set it permanently for your profile:
  ```bash
  secretvault context set --workspace <workspace-slug>
  ```

---

### `ERROR: Project not found matching: '...'`
- Ensure that the project exists in the active workspace.
- Run `secretvault project list` to check all accessible projects.

---

### `ERROR: Network communication failure: Connection refused`
- The SecretVault backend service is not running or the server URL is misconfigured.
- Check backend service status (`curl http://localhost:8080/api/v1/health`).
- Update your server URL with `secretvault config set server http://localhost:8080`.
