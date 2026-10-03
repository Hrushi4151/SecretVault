# SecretVault Phase 12 — Repository Security & Secret Leak Detection Platform
## Implementation Plan & Technical Architecture Audit

---

## 1. Executive Summary & Objective

Phase 12 transforms SecretVault into an enterprise **Repository Security & Secret Leak Detection Platform**. The system detects exposed credentials, private keys, tokens, and sensitive strings across working repositories, Git commits, full Git history, branches, tags, pull requests, CI/CD configs, IaC manifests (Terraform, Kubernetes, Docker), and uploaded source archives.

The platform executes a multi-stage pipeline:
$$\text{Detect} \longrightarrow \text{Classify} \longrightarrow \text{Validate} \longrightarrow \text{Correlate} \longrightarrow \text{Risk-Rank} \longrightarrow \text{Associate Inventory} \longrightarrow \text{Notify} \longrightarrow \text{Remediate} \longrightarrow \text{Rotate/Revoke} \longrightarrow \text{Audit} \longrightarrow \text{Verify}$$

---

## 2. Technical Architecture Audit & Baseline

### 2.1 Backend Architecture
- **Framework:** Spring Boot 3.3.4, Java 21, Spring Data JPA, Flyway, PostgreSQL / H2 test profile.
- **Tenant Scoping:** Strict workspace boundary enforced on every entity via `workspace_id UUID NOT NULL`.
- **Existing Reusable Services:**
  - `EffectiveAccessService`: Authoritative access decision engine. All repository connections, scan triggers, finding views, and remediation operations authenticate and authorize through here.
  - `RotationService`: Authoritative 21-state rotation engine with zero-downtime dual credentials and shadow rotation.
  - `SecretLeaseService`: Dynamic lease governance and automatic revocation.
  - `SecretConsumerService`: Workload consumer registry and heartbeat tracker.
  - `SecurityPostureService` & `SecurityFindingService`: Security Center dashboard and posture evaluation.
  - `AuditService`: Append-only, tamper-evident audit ledger.
  - `OutboxEventPublisher` & `EventDispatcher`: Transactional outbox domain event publishing.
  - `RedisDistributedLockManager`: Distributed mutual exclusion for scan worker claiming and deduplication.

### 2.2 Security Invariant: Zero Secret Exfiltration
The leak detection scanner must **NEVER** become an exfiltration or leak vector:
- The database, log files, audit entries, API responses, frontend state, notifications, SARIF reports, and CLI output **MUST NEVER** contain the raw plaintext secret.
- Only cryptographic SHA-256 fingerprints, masked previews (e.g., `ghp_************9F2A`), detector names, severity, confidence, commit SHA, file path, line/column coordinates, and entropy scores are retained.

---

## 3. Database Schema Design (`V18__repository_security_and_secret_leak_detection.sql`)

### 3.1 New Tables
1. `repositories`:
   `id`, `workspace_id`, `provider`, `external_repository_id`, `owner`, `name`, `default_branch`, `visibility` (`PUBLIC`, `PRIVATE`, `INTERNAL`), `status` (`ACTIVE`, `ARCHIVED`, `DISABLED`), `clone_url`, `auth_credential_encrypted`, `last_scan_at`, `last_successful_scan_at`, `last_commit_sha`, `created_by`, `created_at`, `updated_at`.
2. `repository_security_policies`:
   `id`, `workspace_id`, `repository_id` (NULL for workspace-wide), `scan_on_push`, `scan_pr`, `scan_history`, `entropy_detection_enabled`, `live_validation_enabled`, `fail_ci_severity` (`CRITICAL`, `HIGH`, `MEDIUM`, `LOW`), `max_history_depth`, `excluded_paths_json`, `created_at`, `updated_at`.
3. `repository_scans`:
   `id`, `workspace_id`, `repository_id`, `scan_type` (`FULL`, `INCREMENTAL`, `COMMIT`, `BRANCH`, `PR`, `DIRECTORY`, `ARCHIVE`), `status` (`QUEUED`, `CLONING`, `INDEXING`, `SCANNING`, `CLASSIFYING`, `VALIDATING`, `FINALIZING`, `COMPLETED`, `FAILED`, `CANCELLED`), `commit_sha`, `base_sha`, `head_sha`, `branch`, `files_scanned`, `commits_scanned`, `findings_count`, `high_risk_count`, `duration_ms`, `error_message`, `correlation_id`, `started_at`, `completed_at`, `created_at`.
4. `secret_findings`:
   `id`, `workspace_id`, `repository_id`, `scan_id`, `fingerprint`, `detector_type`, `secret_type`, `severity` (`INFO`, `LOW`, `MEDIUM`, `HIGH`, `CRITICAL`), `confidence` (`LOW`, `MEDIUM`, `HIGH`, `VERY_HIGH`), `status` (`DETECTED`, `TRIAGED`, `CONFIRMED`, `FALSE_POSITIVE`, `REMEDIATION_PENDING`, `REMEDIATING`, `ROTATION_PENDING`, `ROTATED`, `REVOKED`, `RESOLVED`, `REOPENED`, `IGNORED`, `EXPIRED`), `visibility`, `branch`, `commit_sha`, `file_path`, `line_number`, `column_number`, `masked_evidence`, `entropy`, `first_seen_at`, `last_seen_at`, `validation_status` (`UNKNOWN`, `ACTIVE`, `INACTIVE`, `EXPIRED`, `INVALID`, `VALIDATION_FAILED`), `secret_id` (correlated SecretVault secret), `created_at`, `updated_at`.
5. `secret_finding_occurrences`:
   `id`, `finding_id`, `workspace_id`, `repository_id`, `commit_sha`, `branch`, `file_path`, `line_number`, `column_number`, `first_seen_at`, `last_seen_at`.
6. `finding_allowlists`:
   `id`, `workspace_id`, `repository_id`, `fingerprint`, `detector_type`, `file_pattern`, `reason`, `created_by`, `expires_at`, `created_at`.
7. `finding_remediation_jobs`:
   `id`, `workspace_id`, `finding_id`, `remediation_action` (`MARK_FALSE_POSITIVE`, `IGNORE`, `ROTATE_SECRET`, `REVOKE_SECRET`, `REWRITE_HISTORY`), `status`, `actor_id`, `details_json`, `created_at`, `completed_at`.

---

## 4. Scanning Architecture & Pipeline

```
┌────────────────────────────────────────────────────────┐
│                   Source Acquisition                   │
│   (Git Clone/Fetch, PR Diff, Local Dir, Safe Archive)  │
└──────────────────────────┬─────────────────────────────┘
                           │
                           ▼
┌────────────────────────────────────────────────────────┐
│               File Discovery & Exclusions              │
│ (Path Normalization, Symlink Defense, Size Thresholds) │
└──────────────────────────┬─────────────────────────────┘
                           │
                           ▼
┌────────────────────────────────────────────────────────┐
│                Pattern & Entropy Engine                │
│ (20+ Provider Regexes, Generic Credentials, Shannon H) │
└──────────────────────────┬─────────────────────────────┘
                           │
                           ▼
┌────────────────────────────────────────────────────────┐
│              Context & False-Positive Filter           │
│   (Assignment Structure, Key Names, Sample Exclusions) │
└──────────────────────────┬─────────────────────────────┘
                           │
                           ▼
┌────────────────────────────────────────────────────────┐
│       Fingerprinting, Classification & Validation      │
│  (SHA-256 Fingerprint, Masked Evidence, Live Check)    │
└──────────────────────────┬─────────────────────────────┘
                           │
                           ▼
┌────────────────────────────────────────────────────────┐
│          Deduplication & SecretVault Correlation       │
│  (Cluster Occurrences, Inventory Matching, Posture)    │
└──────────────────────────┬─────────────────────────────┘
                           │
                           ▼
┌────────────────────────────────────────────────────────┐
│         Remediation, Rotation & Incident Cascade       │
│ (Audit Ledger, RotationService, Security Center Alerts)│
└────────────────────────────────────────────────────────┘
```

### 4.1 Sandboxing & Path Traversal Protections
1. **Safe Process Execution:** Safe process invocation using argument arrays (`ProcessBuilder`), strictly prohibiting `/bin/sh -c` with concatenated user inputs.
2. **Git Hook Disabling:** Clones and operations run with `GIT_CONFIG_NOSYSTEM=1` and `-c core.hooksPath=/dev/null` to prevent arbitrary repository code execution.
3. **Symlink Escape Defense:** Symlinks pointing outside the temporary repository root or cyclical symlinks are resolved via `toRealPath()` and skipped.
4. **Archive Bomb Defenses:** Zip/Tar extraction enforces max decompressed size (500MB), max file count (10,000), max compression ratio (100:1), and blocks Zip Slip paths containing `..`.
5. **Resource Limits:** Max file size 5MB (skips binaries/large generated artifacts), bounded regex execution time to avoid catastrophic backtracking (ReDoS).

---

## 5. Secret Detection Taxonomy & Detectors

| Detector | Pattern / Rule | Secret Type Classification |
| :--- | :--- | :--- |
| **AWS Access Key** | `\b((?:AKIA|ABIA|ACCA|ASIA)[0-9A-Z]{16})\b` | `AWS_ACCESS_KEY` |
| **AWS Secret Key** | `(?i)aws(.{0,20})?(?-i)['\"][0-9a-zA-Z\/+]{40}['\"]` | `AWS_SECRET_KEY` |
| **GitHub Token** | `\b(gh[pousr]_[A-Za-z0-9_]{36,255})\b` | `GITHUB_TOKEN` |
| **GitLab Token** | `\b(glpat-[0-9a-zA-Z\-_]{20,255})\b` | `GITLAB_TOKEN` |
| **Google API Key** | `\b(AIza[0-9A-Za-z\-_]{35})\b` | `GOOGLE_API_KEY` |
| **Stripe Secret Key** | `\b(sk_(?:test|live)_[0-9a-zA-Z]{24,99})\b` | `STRIPE_KEY` |
| **Slack Token** | `\b(xox[baprs]-[0-9a-zA-Z]{10,48})\b` | `SLACK_TOKEN` |
| **Twilio API Key** | `\b(SK[0-9a-fA-F]{32})\b` | `TWILIO_KEY` |
| **SendGrid Key** | `\b(SG\.[a-zA-Z0-9_\-\.]{66})\b` | `SENDGRID_KEY` |
| **Private Key / SSH**| `-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----` | `PRIVATE_KEY` |
| **JWT Token** | `\beyJ[A-Za-z0-9\-_]{10,}\.eyJ[A-Za-z0-9\-_]{10,}\.[A-Za-z0-9\-_]{10,}\b` | `JWT` |
| **Generic API Key** | `(?i)(?:api[_-]?key|access[_-]?token|secret[_-]?key)\s*[:=]\s*['\"][a-zA-Z0-9_\-]{16,128}['\"]` | `GENERIC_API_KEY` |
| **High Entropy** | Shannon entropy $H \ge 4.5$ on random alphanumeric strings $\ge 20$ chars | `HIGH_ENTROPY_STRING` |

---

## 6. Rotation Integration & Emergency Compromise Workflow

When a detected credential is confirmed active and compromised:
1. Finding status transitions to `CONFIRMED`.
2. If mapped to a known SecretVault secret (`secret_id` is present):
   - Finding triggers `RotationImpactService.analyzeImpact(...)` to inspect affected environments, active leases, and registered consumers.
   - Invokes `RotationService.triggerEmergencyRotation(...)`.
   - Forces revocation of all existing leases via `SecretLeaseService.revokeLease(...)`.
   - Sends reload notification signals to registered consumers via `SecretConsumerService`.
   - Emits `SECRET_COMPROMISED` and `SECURITY_INCIDENT_CREATED` domain events.
   - Automatically marks finding as `RESOLVED` once emergency rotation completes and verification passes.

---

## 7. Implementation Roadmap & Order of Work

1. **Database Migration (`V18`):** Create repository, scan, finding, occurrence, policy, and allowlist tables.
2. **Core Domain Models & Entities:** Java JPA entities and repositories in `com.secretvault.repository.entity` and `repository`.
3. **Detection Engine & Algorithms:**
   - `SecretDetector` interface, `RegexSecretDetector`, `EntropyEvaluator`, `ContextAnalyzer`, `SecretFingerprinter`.
4. **Scanner & Git Sandbox Engine:**
   - `GitRepositoryScanner`, `GitHistoryScanner`, `IncrementalScanEngine`, `ArchiveScanner`, `PathTraversalGuard`.
5. **Live Credential Validator:**
   - Safe HTTP validators for AWS and GitHub with rate limiting, timeouts, and safe endpoint restrictions.
6. **Services & Business Logic:**
   - `RepositoryService`, `RepositoryScanService`, `SecretFindingService`, `FindingRemediationService`, `RepositoryPolicyService`.
7. **REST Controllers & API:**
   - `RepositoryController`, `RepositoryScanController`, `SecretFindingController`, `RepositoryPolicyController`.
8. **WhyExposed & Security Center Integration:**
   - Correlate repository findings with `SecurityPostureService` and explainable WhyExposed breakdowns.
9. **CLI Extensions (`secretvault scan`, `secretvault repository`, `secretvault finding`):**
   - Commands, SARIF output generator, exit code conventions, pre-commit flag support.
10. **SDK Extensions:**
    - Java client methods for repository security and scan triggers.
11. **Frontend UI (`RepositorySecurityView.jsx`):**
    - Repositories tab, Scans dashboard, Findings table with masked evidence and triage modals, Policies tab, WhyExposed modal.
12. **Comprehensive Verification & Tests:**
    - Unit tests, malicious repository tests (symlinks, zip bombs, ReDoS, Git injection), multi-tenant isolation tests, E2E compromise flow, Vitest frontend tests.
