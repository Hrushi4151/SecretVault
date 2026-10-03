# Phase 12: Repository Security & Secret Leak Detection Platform

## Executive Summary

Phase 12 delivers SecretVault's native, enterprise-grade **Repository Security and Secret Leak Detection Platform**. The system provides real-time, zero-plaintext-leak secret scanning across Git repositories, local workspaces, historical commit graphs, branches, tags, pull requests, CI/CD pipelines, and uploaded archives (e.g. zip deployments).

The platform continuously executes the complete vulnerability lifecycle:
$$\text{Detect} \longrightarrow \text{Classify} \longrightarrow \text{Validate} \longrightarrow \text{Correlate} \longrightarrow \text{Risk-Rank} \longrightarrow \text{Notify} \longrightarrow \text{Remediate} \longrightarrow \text{Verify}$$

---

## Key Capabilities

1. **Sandboxed Scanning Execution Engine**:
   - Executes inside isolated scratch directories with strict canonical path traversal validation (`PathTraversalGuard`).
   - Hardened against ZipSlip and ZipBomb vectors (`ArchiveScanner`).
   - Invokes Git via safe `ProcessBuilder` with argument arrays, `GIT_CONFIG_NOSYSTEM=1`, and `core.hooksPath=/dev/null` to eliminate arbitrary command execution.

2. **Multi-Signal Secret Detection**:
   - 14 high-precision regex detectors for major cloud providers, VCS platforms, payment processors, communication gateways, and database URIs.
   - Shannon entropy evaluator ($H = -\sum p_i \log_2 p_i$) with adaptive thresholds for Base64 ($H \ge 4.5$) and Hexadecimal ($H \ge 3.0$).
   - Context analyzer evaluating variable assignments, keyword proximity, and automated test/sample placeholder suppression.

3. **Zero-Plaintext Leak Invariant**:
   - Strict architectural invariant: Plaintext secrets are **never** persisted in the database, logged to stdout/stderr, returned via REST APIs, or exported in SARIF/JSON reports.
   - Uses SHA-256 cryptographic fingerprinting for cross-repository deduplication and non-reversible masked previews (e.g. `AKIA************MP12`).

4. **Live Credential Validation & Inventory Correlation**:
   - Non-destructive API validation against providers (AWS STS `GetCallerIdentity`, GitHub `/user`, Stripe `/v1/balance`).
   - Cryptographic correlation with SecretVault's managed secret inventory (`SecretInventoryCorrelator`).

5. **Automated Remediation & Zero-Downtime Rotation Cascade**:
   - Direct integration with SecretVault's Phase 12 Rotation Engine (`RotationService`).
   - One-click and automated emergency dual-credential rotation for compromised managed secrets.

6. **Unified Developer & SecOps Interfaces**:
   - Web UI: Modern glassmorphic Repository Security console with live scans, findings breakdown, "Why Was This Flagged?" explainability drawer, and remediation wizard.
   - CLI: `secretvault scan` command with Git history traversal, pre-commit staged mode, and SARIF v2.1.0 output for CI/CD gates.
   - SDK: First-class Java SDK client (`client.repositorySecurity()`).

---

## Architecture Topology

```
+-----------------------------------------------------------------------------------+
|                           Ingestion & Inbound Triggers                            |
|  [CLI: secretvault scan]   [Webhooks / PRs]   [Archive Uploads]   [Scheduled Scans] |
+-----------------------------------------+-----------------------------------------+
                                          |
                                          v
+-----------------------------------------------------------------------------------+
|                        Sandboxed Ingestion & Safe Isolation                       |
|           [PathTraversalGuard]  [ArchiveScanner]  [GitProcessExecutor]            |
+-----------------------------------------+-----------------------------------------+
                                          |
                                          v
+-----------------------------------------------------------------------------------+
|                       Phase 12 Detection & Analysis Engine                        |
|  +--------------------+  +----------------------+  +--------------------------+  |
|  | Regex Detectors    |  | Shannon Entropy      |  | Context Analyzer         |  |
|  | (14 Curated Types) |  | (H >= 4.5 / H >= 3.0)|  | (Assignment & Keywords)  |  |
|  +--------------------+  +----------------------+  +--------------------------+  |
+-----------------------------------------+-----------------------------------------+
                                          |
                                          v
+-----------------------------------------------------------------------------------+
|                       Zero Plaintext Cryptographic Pipeline                       |
|         [SecretFingerprinter: SHA-256] ----> [Non-Reversible Masking]             |
+-----------------------------------------+-----------------------------------------+
                                          |
                                          v
+-----------------------------------------------------------------------------------+
|                     Enrichment, Correlation & Remediation                         |
|  +------------------------+  +------------------------+  +--------------------+   |
|  | Live Credential        |  | SecretVault Inventory  |  | Rotation Engine    |   |
|  | Validator (STS/OAuth)  |  | Correlator             |  | Emergency Cascade  |   |
|  +------------------------+  +------------------------+  +--------------------+   |
+-----------------------------------------------------------------------------------+
```

---

## Documentation Index

| Document | Description |
| :--- | :--- |
| [ARCHITECTURE.md](ARCHITECTURE.md) | Architectural subsystems, isolation boundaries, sequence flows |
| [SCANNER.md](SCANNER.md) | File discovery, Git commit parsing, and sandboxed execution |
| [DETECTION_ENGINE.md](DETECTION_ENGINE.md) | Regex, Shannon entropy analysis, keyword heuristics, confidence scoring |
| [SECRET_PATTERNS.md](SECRET_PATTERNS.md) | Specification of the 14 credential detectors and validation patterns |
| [GIT_HISTORY.md](GIT_HISTORY.md) | Commit graph traversal, diff generation, branch/tag scanning |
| [REPOSITORIES.md](REPOSITORIES.md) | Repository entities, source adapters, connection types |
| [PULL_REQUESTS.md](PULL_REQUESTS.md) | PR scanning, pre-receive hooks, commit status checks |
| [CI_CD.md](CI_CD.md) | GitHub Actions, GitLab CI, Jenkins pipeline integration with SARIF |
| [POLICIES.md](POLICIES.md) | Repository security policies, path exclusions, entropy thresholds |
| [FINDINGS.md](FINDINGS.md) | Finding data model, lifecycle states, triage, and allowlisting |
| [REMEDIATION.md](REMEDIATION.md) | Workflows for revocation, Git rewrite guides, false-positive handling |
| [ROTATION_INTEGRATION.md](ROTATION_INTEGRATION.md) | Emergency rotation cascade through `RotationService` |
| [SECURITY.md](SECURITY.md) | Zero-plaintext invariants, ReDoS safety, command injection defenses |
| [THREAT_MODEL.md](THREAT_MODEL.md) | STRIDE threat modeling and mitigation proofs |
| [PERFORMANCE.md](PERFORMANCE.md) | Throughput, memory limits, streaming diff processing |
| [CHAOS.md](CHAOS.md) | Adversarial test suite, zip bombs, symlink loops, ReDoS payloads |
| [CLI.md](CLI.md) | `secretvault scan` CLI commands, flags, exit codes, and SARIF output |
| [API.md](API.md) | REST API endpoints, schemas, authentication, and error codes |
| [OPERATIONS.md](OPERATIONS.md) | Production deployment, resource limits, monitoring metrics |
| [RUNBOOK.md](RUNBOOK.md) | Incident response procedures and on-call operational playbook |
| [DISASTER_RECOVERY.md](DISASTER_RECOVERY.md) | Backup, recovery, and data integrity safeguards |
| [IMPLEMENTATION_REPORT.md](IMPLEMENTATION_REPORT.md) | Detailed accounting of delivered components |
| [TEST_REPORT.md](TEST_REPORT.md) | Verification and test coverage matrix |
| [PRODUCTION_CERTIFICATION.md](PRODUCTION_CERTIFICATION.md) | Formal production readiness certification |
