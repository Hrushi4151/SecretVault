package com.secretvault.ai.knowledge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Authoritative SecretVault Platform Knowledge Service.
 * Provides grounded platform architecture, encryption models, operational mechanics,
 * CLI/SDK integration, Kubernetes/Terraform specs, and security policies for AI Copilot grounding.
 */
@Service
public class AiPlatformKnowledgeService {

    private static final Logger log = LoggerFactory.getLogger(AiPlatformKnowledgeService.class);

    public record KnowledgeArticle(
            String topic,
            String title,
            String category,
            List<String> keywords,
            String content
    ) {}

    private final Map<String, KnowledgeArticle> knowledgeBase = new LinkedHashMap<>();

    public AiPlatformKnowledgeService() {
        initializeKnowledgeBase();
    }

    private void initializeKnowledgeBase() {
        // 1. Architecture & Security Model
        register(new KnowledgeArticle(
                "architecture_overview",
                "SecretVault Architecture & Zero-Knowledge Security Model",
                "ARCHITECTURE",
                List.of("architecture", "overview", "security model", "zero-knowledge", "envelope encryption", "kms"),
                """
                SecretVault is a production-grade DevSecOps Secret Management & Security Control Plane.
                Core Architectural Pillars:
                1. Envelope Encryption: All secrets are encrypted using unique AES-256-GCM Data Encryption Keys (DEKs). DEKs are protected by Root Key Encryption Keys (KEKs) hosted in hardware KMS / HSM.
                2. Zero Plaintext Invariant: Plaintext secrets are strictly ephemeral in-memory, never persisted to logs, audit events, telemetry, or AI contexts. Memory buffers are zeroized immediately after crypto operations.
                3. Multi-Tenancy Hierarchy: Multi-tenant scoping strictly enforces: Organization -> Workspace -> Project -> Environment -> Secret. Cross-tenant or cross-workspace access is mathematically and structurally impossible.
                4. Four-Eyes Approval: High-risk actions (critical secret deletion, master key rotation, production sync overrides) require two independent authorized actors.
                5. Step-Up Authentication: Privileged operations require continuous re-authentication (WebAuthn / Passkeys / FIDO2 / TOTP).
                """
        ));

        // 2. Encryption & KMS
        register(new KnowledgeArticle(
                "encryption_kms",
                "KMS Envelope Encryption & Key Lifecycle",
                "ENCRYPTION",
                List.of("encryption", "kms", "aes-256-gcm", "dek", "kek", "hkdf", "key rotation", "envelope"),
                """
                SecretVault Cryptographic Lifecycle:
                - Algorithm: AES-256-GCM authenticated encryption with 128-bit authentication tags and 96-bit unique nonces per encryption operation.
                - Key Derivation: HKDF-SHA256 is used for subkey generation and contextual key derivation with workspace/project domain separation tags.
                - Master Key Rotation: Root KEKs can be rotated without re-encrypting underlying payloads using envelope re-wrapping.
                - Versioning: Every secret modification produces an immutable version snapshot with a cryptographic SHA-256 digest prefix for zero-knowledge diff and drift validation.
                """
        ));

        // 3. Access Control, RBAC, & JIT
        register(new KnowledgeArticle(
                "rbac_jit_access",
                "RBAC, Granular Permissions, and Just-In-Time (JIT) Access",
                "ACCESS_CONTROL",
                List.of("rbac", "jit", "permissions", "roles", "just-in-time", "elevation", "owner", "admin", "developer", "viewer"),
                """
                SecretVault Access Control Engine (EffectiveAccessService):
                - Standing Roles: OWNER (full tenant control), ADMIN (workspace administration), DEVELOPER (read/write non-prod secrets, read-only metadata in prod), VIEWER (read-only safe metadata).
                - Granular Resource Grants: Scoped down to specific Projects, Environments, or individual Secret paths.
                - Just-In-Time (JIT) Temporary Access: Users can request time-bounded access (15m to 8h) with mandatory business justification, auto-revocation, and four-eyes manager approval for production scopes.
                - Effective Permissions: The engine dynamically calculates Union(Standing Roles, Explicit Grants, Active JIT) intersected with Environment Safety Policies.
                """
        ));

        // 4. MFA, WebAuthn, & Passkeys
        register(new KnowledgeArticle(
                "mfa_webauthn",
                "Multi-Factor Authentication & WebAuthn / Passkeys",
                "AUTHENTICATION",
                List.of("mfa", "webauthn", "passkey", "fido2", "totp", "step-up", "biometric", "hardware key"),
                """
                SecretVault Authentication & Step-Up Security:
                - WebAuthn / FIDO2: Hardware security keys (YubiKey) and platform biometrics (TouchID, Windows Hello) provide phishing-resistant authentication with user presence and user verification flags.
                - TOTP MFA: RFC 6238 compliant time-based one-time passwords with encrypted secret seeds and single-use backup recovery codes.
                - Step-Up Policy: Modifying production secrets, initiating emergency sync, or triggering AI remediation execution automatically triggers Step-Up verification before execution.
                """
        ));

        // 5. Secret Rotation & Leases
        register(new KnowledgeArticle(
                "rotation_leases",
                "Automated Secret Rotation, Leases, and Zero-Downtime Rollout",
                "ROTATION",
                List.of("rotation", "lease", "dual-version", "rollover", "consumer", "shadow validation", "overdue"),
                """
                Secret Rotation Engine:
                - Policies: Configurable rotation interval (e.g., 30d, 90d), max age, and automatic warning thresholds.
                - Dual-Version Tolerance Window: When a secret rotates, both Version N and Version N-1 remain valid during a configurable overlap window (default 300 seconds) preventing service interruption.
                - Consumer Lease Tracking: Applications register heartbeats; rotation scheduler verifies all active consumers have acknowledged the new secret version before decommissioning the old version.
                - Shadow Validation: Test credentials against upstream database/API before promoting to active state.
                """
        ));

        // 6. Cloud Provider Sync & Drift Detection
        register(new KnowledgeArticle(
                "sync_and_drift",
                "Multi-Cloud Secret Synchronization & Drift Engine",
                "SYNCHRONIZATION",
                List.of("sync", "drift", "aws", "gcp", "azure", "vault", "kubernetes", "reconciliation", "providers"),
                """
                Synchronization & Drift Engine:
                - Supported Providers: AWS Secrets Manager, GCP Secret Manager, Azure Key Vault, HashiCorp Vault, Kubernetes Secrets.
                - Desired vs Actual State: Regularly compares authoritative SecretVault state with downstream provider state using zero-knowledge hash digests.
                - Drift Detection: Detects Out-of-Band modifications, missing secrets, tag mismatches, and permission errors.
                - Auto-Reconciliation: Can idempotently push authoritative versions or alert security teams upon drift discovery.
                """
        ));

        // 7. Kubernetes Operator & CRDs
        register(new KnowledgeArticle(
                "kubernetes_integration",
                "Kubernetes Operator, CRDs, and Mutating Admission Webhook",
                "INTEGRATION",
                List.of("kubernetes", "k8s", "crd", "operator", "webhook", "sidecar", "daemon", "secretvaultsecret"),
                """
                Kubernetes Integration Architecture:
                - Custom Resource Definitions (CRDs):
                  * SecretVaultSecret: Maps SecretVault secret paths into native Kubernetes Secret objects.
                  * SecretVaultSync: Configures continuous synchronization and refresh intervals.
                  * SecretVaultProvider: Declares provider authentication credentials via SPIFFE/OIDC.
                - Mutating Webhook: Intercepts Pod creation to inject in-memory volume projections or sidecars, preventing secrets from touching container filesystem disks.
                """
        ));

        // 8. Terraform Provider
        register(new KnowledgeArticle(
                "terraform_provider",
                "SecretVault Terraform Provider & Infrastructure as Code",
                "IAC",
                List.of("terraform", "iac", "hcl", "provider", "secretvault_secret", "secretvault_project"),
                """
                Terraform Provider Capabilities:
                - Resources: `secretvault_project`, `secretvault_environment`, `secretvault_secret`, `secretvault_rotation_policy`, `secretvault_provider_integration`, `secretvault_access_grant`.
                - Drift Management: Terraform plan inspects metadata hashes and flags out-of-band updates.
                - Ephemeral Sensitive State: Prevents secret values from being written in unencrypted plaintext to `.tfstate` files using write-only attributes.
                """
        ));

        // 9. CLI & SDK Operational Behavior
        register(new KnowledgeArticle(
                "cli_sdk_architecture",
                "CLI and Client SDK Architecture",
                "CLIENTS",
                List.of("cli", "sdk", "api", "diagnostics", "cache", "keyring", "spiffe", "machine identity"),
                """
                CLI & SDK Operational Model:
                - CLI (`secretvault`): Communicates with the backend REST and AI APIs. Uses OS keyring (Keychain, SecretService, Credential Manager) for secure session token storage.
                - SDKs (Java, Python, Go, Node.js): Provide memory-scrubbed secret retrieval, dynamic lease renewal, automatic in-memory caching with TTL, and seamless AI diagnostics reporting.
                - Machine Identities: Support workload identity federation (OIDC tokens from GitHub Actions, AWS IAM, GCP Workload Identity, Kubernetes Service Accounts) without long-lived static tokens.
                """
        ));

        // 10. Security Posture & Health Scoring
        register(new KnowledgeArticle(
                "security_posture_scoring",
                "Security Posture Score & Risk Calculation",
                "SECURITY",
                List.of("posture", "score", "risk", "findings", "compliance", "decay", "cve"),
                """
                Security Posture Intelligence (0 - 100 Score):
                - Calculation Breakdown:
                  * Rotation Compliance (25%): Penalizes stale secrets exceeding rotation intervals.
                  * Drift Health (20%): Penalizes unmanaged out-of-band drift across cloud targets.
                  * MFA & JIT Coverage (20%): Rewards WebAuthn/MFA enforcement and zero standing admin privileges.
                  * Provider Health (15%): Evaluates sync failure rates and provider auth health.
                  * Security Findings (20%): Penalizes open CVEs, weak key lengths, and public exposure risks.
                """
        ));
    }

    private void register(KnowledgeArticle article) {
        knowledgeBase.put(article.topic().toLowerCase(), article);
    }

    public List<KnowledgeArticle> search(String query, int limit) {
        if (query == null || query.isBlank()) {
            return knowledgeBase.values().stream().limit(limit).toList();
        }

        String normalized = query.toLowerCase();
        List<String> tokens = Arrays.stream(normalized.split("[\\s,;:.?!]+"))
                .filter(t -> t.length() > 2)
                .toList();

        return knowledgeBase.values().stream()
                .map(article -> {
                    int score = 0;
                    if (article.topic().contains(normalized)) score += 10;
                    if (article.title().toLowerCase().contains(normalized)) score += 8;
                    if (article.content().toLowerCase().contains(normalized)) score += 4;
                    for (String kw : article.keywords()) {
                        if (normalized.contains(kw.toLowerCase())) score += 6;
                        for (String token : tokens) {
                            if (kw.toLowerCase().contains(token)) score += 3;
                        }
                    }
                    return Map.entry(article, score);
                })
                .filter(e -> e.getValue() > 0)
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(limit)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    public Optional<KnowledgeArticle> getTopic(String topic) {
        if (topic == null) return Optional.empty();
        return Optional.ofNullable(knowledgeBase.get(topic.toLowerCase().trim()));
    }

    public List<KnowledgeArticle> getAllArticles() {
        return new ArrayList<>(knowledgeBase.values());
    }
}
