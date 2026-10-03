# SecretVault CLI — Security Controls & Threat Model

This document outlines the security architecture, memory protections, threat mitigations, and redaction controls implemented in the SecretVault CLI.

---

## 1. Core Security Principles

1. **Untrusted Client Architecture:**
   - The CLI is an untrusted endpoint. All authorization decisions, RBAC checks, rate limits, and cryptographic operations are enforced authoritatively by the SecretVault backend.
2. **Zero Plaintext Persistence:**
   - Secrets and session tokens are never saved in plaintext configuration files (`config.json`).
3. **No Direct Database Access:**
   - The CLI communicates strictly over REST APIs (HTTPS) and never accesses PostgreSQL, Redis, KMS keys, or Master Keys directly.
4. **Command Injection Immunity:**
   - The CLI executes child processes using raw argument arrays (`ProcessBuilder(List<String>)`), completely bypassing shell interpreters (`/bin/sh -c`) to eliminate command injection vulnerabilities.

---

## 2. Threat Model & Mitigations

| Threat | Risk Impact | CLI Mitigation |
| :--- | :--- | :--- |
| **1. Stolen CLI Token** | Unauthorized API access | Short-lived JWTs (24h default); encrypted token storage with AES-256-GCM + PBKDF2; explicit token revocation on `logout`. |
| **2. Shell History Leakage** | Plaintext secrets in `~/.bash_history` | The CLI rejects `--password` arguments and mandates `--password-stdin` or interactive non-echoing console input. |
| **3. Accidental `.env` Commit** | Plaintext secret exposure in git repositories | `secretvault env pull` checks `.gitignore` and alerts the user if `.env` is unignored. |
| **4. Malicious `.env` File** | Arbitrary code execution via subshell expansion | Safe data-only parser treats `$(...)` and backticks as literal data without spawning shells. |
| **5. Command Injection via `run`** | Remote or local shell command injection | Direct process execution via argument arrays; avoids `/bin/sh -c` string evaluation. |
| **6. Stale Credentials in Logs** | Token / Secret leakage in error dumps | Centralized `RedactionHelper` filters Bearer headers, JWTs, and secret keys from all exceptions and diagnostic logs. |
| **7. Cross-Profile Token Leakage** | Credentials sent to wrong server origin | Credential storage binds tokens strictly to `profile@serverUrl` keys; never sends tokens to unauthenticated endpoints. |
| **8. Insecure Production HTTP** | Plaintext HTTP traffic interception | Client enforces HTTPS for remote hosts (allows HTTP exclusively for `localhost` and `127.0.0.1`). |
| **9. Process Environment Snooping** | Unrelated secrets exposed to child process | Allows explicit secret allow-lists (`--secret KEY`) so only required secrets are passed into memory. |
| **10. Zombie Child Processes** | Orphaned processes running in background on CLI exit | JVM shutdown hooks intercept `SIGINT`/`SIGTERM` and destroy the entire child process hierarchy. |
| **11. Unauthorized Secret Exfiltration** | Accidental or malicious secret exposure | Hierarchical `/reveal-policy` evaluation, mandatory audit reason, contextual Step-Up challenge, and single-use `X-Reveal-Intent-Token`. |
| **12. Weak or Phished Passwords** | Unauthorized login attempts | Mandatory interactive MFA (TOTP / Recovery Code) challenge during `secretvault auth login`. |

---

## 3. Centralized Redaction

All log output, debug messages, and error responses pass through `com.secretvault.cli.security.RedactionHelper`:

- `Authorization: Bearer <token>` ➔ `Authorization: [REDACTED]`
- `X-Step-Up-Proof: <token>` ➔ `X-Step-Up-Proof: [REDACTED]`
- `X-Reveal-Intent-Token: <token>` ➔ `X-Reveal-Intent-Token: [REDACTED]`
- `eyJ...` (JWT Tokens) ➔ `[REDACTED_JWT]`
- `{"password": "..."}` ➔ `{"password": "[REDACTED]"}`
- `{"secret": "..."}` ➔ `{"secret": "[REDACTED]"}`
- `{"totpCode": "..."}` ➔ `{"totpCode": "[REDACTED]"}`
- `{"recoveryCode": "..."}` ➔ `{"recoveryCode": "[REDACTED]"}`

Plaintext secrets are rendered **exclusively** on stdout when the user explicitly runs `secretvault secret reveal` or `secretvault env pull`.

---

## 4. Memory Zeroing & Ephemeral Secret Handling

Sensitive credentials, OTPs, recovery codes, and passwords handled by the CLI:
1. Are captured via non-echoing console input (`System.console().readPassword()`) into `char[]` buffers.
2. Are immediately overwritten with null characters (`Arrays.fill(buffer, '\0')`) in `finally` blocks after REST dispatch.
3. Are never assigned to long-lived static variables or unmanaged GC-eligible string instances.
4. Step-Up challenge tokens and reveal intent tokens are scoped to single-use lifecycle and zeroized after execution.
