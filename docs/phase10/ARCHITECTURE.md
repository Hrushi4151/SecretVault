# SecretVault CLI — Architecture & Internal Design

This document details the modular software architecture of the SecretVault CLI, illustrating the separation of concerns between authentication, secure storage, REST communication, context resolution, and process runtime injection.

---

## 1. High-Level Modular Design

```
cli/
 ├── com.secretvault.cli
 │    ├── SecretVaultCli.java          # Picocli root command & argument dispatcher
 │    │
 │    ├── auth/                        # Authentication & Credential Storage
 │    │    ├── AuthManager.java        # Coordinates login, auto-refresh, and credentials
 │    │    ├── AuthenticationProvider  # Abstraction for Human auth & future Machine/OIDC auth
 │    │    ├── CredentialStore.java    # Secure token persistence contract
 │    │    └── EncryptedFileCredentialStore.java # AES-256-GCM + PBKDF2 implementation
 │    │
 │    ├── client/                      # REST API Client (java.net.http.HttpClient)
 │    │    ├── SecretVaultApiClient.java # Endpoints for Auth, Workspaces, Secrets, Versions
 │    │    ├── ApiClientException.java # Structured error classifications
 │    │    └── dto/                    # API DTO models matching backend contracts
 │    │
 │    ├── config/                      # Non-Sensitive Configuration & Context
 │    │    ├── CliConfig.java          # Profile configurations (NO tokens/passwords)
 │    │    ├── ConfigManager.java      # Platform directory resolver & file permissions
 │    │    ├── ContextManager.java     # Hierarchical context resolution
 │    │    └── ProjectLocalConfig.java # .secretvault/project.json model
 │    │
 │    ├── runtime/                     # Runtime Process Secret Injection
 │    │    ├── SecretInjector.java     # RAM secret fetching & environment composition
 │    │    └── ProcessRunner.java      # ProcessBuilder runner, signal forwarding & exit codes
 │    │
 │    ├── env/                         # Safe .env Parsing & Synchronization
 │    │    ├── SafeDotEnvParser.java   # Data-only tokenizer (no code execution)
 │    │    ├── DotEnvPuller.java       # Safe export with gitignore & chmod 600 checks
 │    │    └── DotEnvPusher.java       # Diff preview calculation & batch pushing
 │    │
 │    ├── security/                    # Redaction & Buffer Wiping
 │    │    └── RedactionHelper.java    # Centralized regex sanitizer for tokens/passwords
 │    │
 │    └── command/                     # Picocli Command Implementations
 │         ├── AuthCommand.java
 │         ├── WorkspaceCommand.java
 │         ├── ProjectCommand.java
 │         ├── EnvironmentCommand.java
 │         ├── ContextCommand.java
 │         ├── SecretCommand.java
 │         ├── EnvCommand.java
 │         ├── RunCommand.java
 │         ├── DevCommand.java
 │         ├── ConfigCommand.java
 │         ├── DoctorCommand.java
 │         ├── VersionCommand.java
 │         └── CompletionCommand.java
```

---

## 2. Compatibility with Future Phases

### Phase 9: Machine Identity & OIDC Compatibility
The `AuthenticationProvider` interface defines a clean contract:
```java
public interface AuthenticationProvider {
    AuthResponse authenticate(SecretVaultApiClient client, String usernameOrClient, char[] passwordOrSecret);
    String getAuthType();
}
```
Future OIDC / Workload Identity Federation (GitHub Actions OIDC, AWS IAM, GCP Workload Identity) can plug directly into `AuthenticationProvider` without modifying command handlers or CLI consumers.

### Phase 11: Developer SDK & Runtime Agent
The `SecretVaultApiClient`, `SecretInjector`, and DTO models are decoupled from terminal printing and Picocli annotations. The Phase 11 SDK can directly consume or mirror these clean abstractions.

---

## 3. Idempotency & Retry Architecture

- Safe idempotent requests (`GET /workspaces`, `GET /secrets`, `GET /health`) automatically handle transient network glitches with exponential backoffs.
- In-memory token renewal automatically catches HTTP 401s, calls `/api/v1/auth/refresh`, and transparently retries the requested API call once.
- Destructive mutations (`POST /secrets`, `DELETE /secrets`) never retry blindly to prevent duplicate version creation.
