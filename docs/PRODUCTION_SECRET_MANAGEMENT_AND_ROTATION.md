# SecretVault — Production Secret Management & Rotation Architecture Runbook

This document defines the production secret store configuration, secret lifecycle management, and zero-downtime rotation/rollback procedures for SecretVault deployments.

---

## 1. Cloud Secret Store Architecture

In production, SecretVault strictly prohibits static credentials committed to source control, baked into container images, or stored in plaintext configuration files. All sensitive credentials are provided dynamically through one of the following enterprise secret stores:

### Supported Secret Stores
1. **AWS Secrets Manager / AWS Systems Manager Parameter Store**:
   - Integrated with AWS IAM Roles for Service Accounts (IRSA) / ECS Task Roles.
   - Secrets referenced via ARN: `arn:aws:secretsmanager:us-east-1:123456789012:secret:secretvault/prod/*`.
2. **HashiCorp Vault**:
   - Integrated via Kubernetes ServiceAccount Token projection (`vault.hashicorp.com/role: secretvault-backend`).
   - KV v2 engine mount path: `secret/data/production/secretvault`.
3. **Kubernetes External Secrets Operator (ESO)**:
   - Synchronizes cloud secrets (`SecretStore` / `ClusterSecretStore`) into native Kubernetes `Secret` objects mounted as read-only environment variables or volumes (`/etc/secrets`).

### Production Secret Inventory
| Secret Name | Source Store Key | Injected Env Var | Usage |
| :--- | :--- | :--- | :--- |
| **PostgreSQL Credentials** | `secretvault/prod/db` | `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` | Primary relational database access |
| **GitHub Webhook Secret** | `secretvault/prod/github` | `GITHUB_WEBHOOK_SECRET` | HMAC-SHA256 signature verification for webhook ingress |
| **Master Encryption Key (KEK)** | AWS KMS or `secretvault/prod/kek` | `VAULT_MASTER_KEY` / `KMS_KEY_ID` | Envelope encryption for customer secrets |
| **JWT Signing Key** | `secretvault/prod/jwt` | `JWT_SECRET` | Authentication tokens for API and user sessions |
| **Redis Password** | `secretvault/prod/redis` | `REDIS_PASSWORD` | Distributed locking, rate limiting, and cache |

---

## 2. Source Control & Image Verification

A security audit of the repository and image definitions was executed:
1. **Source Control Check**:
   - `.gitignore` enforces exclusion of `.env`, `.env.local`, `*.pem`, `*.key`, `*.p12`, `*.jks`, `*.tfvars`, and log files.
   - No plaintext secrets or passwords exist in `src/main/resources/application-prod.yml` (all values mapped via `${ENV_VAR}` placeholders).
2. **Container Image Check**:
   - `infrastructure/docker/Dockerfile.backend` employs multi-stage compilation (`eclipse-temurin:21-jre-alpine`).
   - Only the compiled binary `target/*.jar` is copied into the runtime image.
   - Runtime container runs as an unprivileged non-root user (`USER secretvault:secretvault`).
   - No credentials, `.env` files, or test keys are copied into any layer.
3. **Log & Error Redaction**:
   - All error formatting across webhooks and event dispatching routes through `EventDispatcher.sanitizeErrorMessage(...)` and `BaseDomainEvent.sanitizeHexTokensInText(...)`.
   - Credentials matching GitHub tokens (`ghp_`), AWS keys (`AKIA`), Slack tokens, Stripe keys, PEM private keys, Authorization headers, and high-entropy hex strings are masked prior to emission to stdout or observability collectors.

---

## 3. GitHub Webhook Secret Rotation & Rollback Runbook

GitHub webhooks support dual-secret rotation, allowing seamless zero-downtime secret rollover.

### Rotation Procedure (Zero-Downtime)
1. **Generate New Secret**:
   - Generate a cryptographically secure 256-bit secret:
     ```bash
     openssl rand -hex 32
     ```
2. **Update Cloud Secret Store**:
   - In AWS Secrets Manager or HashiCorp Vault, store the new secret under `secretvault/prod/github/next`.
3. **Deploy Webhook Configuration**:
   - Update the production backend deployment to accept both the active secret and the new candidate secret during the transition window, or deploy the candidate secret to the primary cluster pods.
4. **Update GitHub Webhook**:
   - In GitHub Repository / Organization Settings > Webhooks > Edit:
   - Paste the new secret into the **Secret** field and click **Update Webhook**.
   - GitHub will sign all subsequent deliveries with the new secret immediately.
5. **Verify Deliveries**:
   - Deliver a test ping or commit push event. Confirm HTTP 200 responses in GitHub's **Recent Deliveries** tab.
6. **Retire Old Secret**:
   - Remove the old secret from the cloud store and archive the version.

### Rollback Procedure
If deliveries fail signature verification (HTTP 401) during rotation:
1. In GitHub Webhook settings, revert the **Secret** field to the previous known-good secret.
2. In AWS Secrets Manager / Vault, restore the previous secret version.
3. Restart pod instances to force fresh environment synchronization if cached.
4. Redeliver failed payloads from GitHub's **Recent Deliveries** tab.

---

## 4. PostgreSQL Database Credential Rotation Runbook

To achieve zero-downtime database credential rotation, use a dual-user alternating rotation strategy.

### Rotation Procedure (Zero-Downtime)
1. **Identify Active & Inactive User**:
   - SecretVault supports alternating users `vault_app_blue` and `vault_app_green`.
   - Check current active user:
     ```sql
     SELECT current_user;
     ```
2. **Create / Rotate Password for Inactive User**:
   - If `vault_app_blue` is active, generate a new password for `vault_app_green`:
     ```sql
     ALTER USER vault_app_green WITH PASSWORD '<new_secure_generated_password>';
     GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO vault_app_green;
     ```
3. **Update Cloud Secret Store**:
   - In AWS Secrets Manager / Vault, update `secretvault/prod/db` with username `vault_app_green` and the new password.
4. **Trigger Rolling Deployment**:
   - Perform a rolling restart of the backend pods:
     ```bash
     kubectl rollout restart deployment/secretvault-backend -n prod
     ```
   - New pods boot up, connect to PostgreSQL using `vault_app_green`, and pass readiness probes (`/actuator/health`).
   - Old pods drain existing connections gracefully.
5. **Revoke Previous Password**:
   - Once all traffic has migrated to the new pods, lock or randomize the password of the retired user (`vault_app_blue`).

### Rollback Procedure
If new pods fail connection checks or HikariCP pool errors occur:
1. Immediately revert the secret in AWS Secrets Manager / Vault to the previous username/password (`vault_app_blue`).
2. Rollback the Kubernetes deployment:
   ```bash
   kubectl rollout undo deployment/secretvault-backend -n prod
   ```
3. Verify HikariCP reconnects and `/actuator/health` returns `UP`.
