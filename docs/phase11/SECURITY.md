# SecretVault SDK — Security Architecture & Hardening

## Security Invariants

1. **Authoritative Authorization**: The SDK never bypasses `EffectiveAccessService`. Every request is authorized server-side based on machine identity, workspace, project, environment, and granular grant.
2. **Centralized Redaction**: `RedactionUtil` redacts `Bearer` tokens, passwords, and sensitive keys from logs, exception messages, and debug output.
3. **No Secret Telemetry**: Actuator health and Micrometer metrics never expose secret names or payload values.
4. **TLS Strict Validation**: HTTPS hostname verification and CA certificate chain validation are strictly enforced in production.
