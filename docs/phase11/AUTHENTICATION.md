# SecretVault SDK — Authentication Strategies

## Supported Authentication Modes

1. **`StaticTokenProvider`**: Pre-generated static API token or human access token.
2. **`MachineTokenProvider`**: Machine Identity session token (SHA-256 validated in SecretVault).
3. **`OidcTokenProvider`**: Workload Identity federation with automatic 600-second session exchange and renewal.
4. **`EnvironmentTokenProvider`**: Resolves `SECRET_VAULT_TOKEN`, `SECRETVAULT_TOKEN`, or `SECRET_VAULT_MACHINE_TOKEN`.
5. **`CredentialProviderChain`**: Sequential fallback across providers.

---

## Token Security

- Tokens are strictly held in memory.
- All tokens are redacted from logs and exception messages.
- OIDC exchanges occur over encrypted HTTPS connections with SSRF protections.
