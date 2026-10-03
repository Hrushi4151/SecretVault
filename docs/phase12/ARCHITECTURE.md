# Phase 12 Architecture: Repository Security & Secret Leak Detection

## 1. High-Level System Overview

The SecretVault Repository Security subsystem provides automated detection, triage, live validation, cryptographic inventory correlation, and zero-downtime remediation for leaked credentials.

It is designed around four foundational architectural pillars:
1. **Isolated Sandboxing**: Scans occur strictly inside contained filesystem sandboxes with zero arbitrary shell execution.
2. **Multi-Signal Detection**: Combines bounded deterministic regex matching, Shannon entropy quantification, and keyword contextual scoring.
3. **Zero-Plaintext Invariant**: Scanner components, persistence layers, REST controllers, and audit trails never persist or transmit raw plaintext secrets.
4. **Actionable Remediation**: Findings are directly wired into SecretVault's Phase 12 Rotation Engine for immediate automated or manual key rotation and credential revocation.

---

## 2. Component Architecture Diagram

```mermaid
graph TD
    subgraph Ingestion["1. Ingestion Layer"]
        CLI["secretvault scan (CLI)"]
        WEB["Repository Web UI"]
        API["REST API (/api/v1/workspaces/.../repositories)"]
        GIT_SRC["Git Clone / Working Tree"]
        ZIP_SRC["Zip Archive Upload"]
    end

    subgraph Sandbox["2. Sandboxed Isolation Boundary"]
        GUARD["PathTraversalGuard (Canonical Containment)"]
        ARCH["ArchiveScanner (ZipSlip & Bomb Defense)"]
        EXEC["GitProcessExecutor (Safe Argument Arrays, No Hooks)"]
    end

    subgraph Engine["3. Detection & Analysis Engine"]
        DISC["FileDiscoveryEngine (Binary Probing & Exclusions)"]
        SCAN["GitRepositoryScanner (Tree & Diff History)"]
        DET["RegexSecretDetector (14 High-Value Rules)"]
        ENT["EntropyEvaluator (Shannon Entropy H >= 4.5)"]
        CTX["ContextAnalyzer (Assignment & Keywords)"]
    end

    subgraph Security["4. Zero-Plaintext Cryptographic Core"]
        FP["SecretFingerprinter (SHA-256 Digest)"]
        MASK["MaskedPreviewGenerator (e.g. AKIA************MP12)"]
    end

    subgraph Enrichment["5. Enrichment, RBAC & Remediation"]
        LIVE["LiveCredentialValidator (STS, GitHub, Stripe)"]
        CORR["SecretInventoryCorrelator (Vault Matching)"]
        RBAC["EffectiveAccessService (Tenant Isolation)"]
        AUDIT["AuditService (Tamper-Evident Ledger)"]
        ROT["RotationService (Emergency Key Rotation)"]
    end

    CLI --> API
    WEB --> API
    API --> GUARD
    GIT_SRC --> EXEC
    ZIP_SRC --> ARCH
    ARCH --> GUARD
    EXEC --> GUARD

    GUARD --> DISC
    DISC --> SCAN
    SCAN --> DET
    DET --> ENT
    DET --> CTX

    DET --> FP
    DET --> MASK

    FP --> LIVE
    FP --> CORR
    CORR --> RBAC
    RBAC --> AUDIT
    CORR --> ROT
```

---

## 3. Sequence Flow: End-to-End Scan & Remediation

```mermaid
sequenceDiagram
    autonumber
    actor DevSecOps as DevSecOps Engineer / CI Pipeline
    participant API as RepositoryScanController
    participant ScanSvc as RepositoryScanService
    participant Sandbox as PathTraversalGuard & GitExecutor
    participant Engine as GitRepositoryScanner & Detectors
    participant FP as SecretFingerprinter
    participant LiveVal as LiveCredentialValidator
    participant Correlator as SecretInventoryCorrelator
    participant DB as PostgreSQL (V18 Schema)
    participant RotSvc as RotationService

    DevSecOps->>API: POST /api/v1/workspaces/{wsId}/repositories/{repoId}/scans
    API->>ScanSvc: triggerScan(workspaceId, repoId, request, actorId)
    ScanSvc->>Sandbox: prepareSandbox(repoSource)
    Sandbox-->>ScanSvc: validatedSandboxPath
    ScanSvc->>Engine: scanRepository(sandboxPath, config)
    
    loop For Each File / Git Commit Diff
        Engine->>Engine: scanContent(content, path, commitSha, branch)
        Engine->>FP: computeFingerprint(rawSecret) & computeMaskedPreview()
        FP-->>Engine: fingerprint (SHA-256), maskedValue
        Note over Engine,FP: Raw secret immediately discarded from memory
    end
    
    Engine-->>ScanSvc: List<SecretDetectionResult> (Masked & Fingerprinted)
    
    loop For Each Detected Candidate
        ScanSvc->>LiveVal: validateToken(secretType, rawToken) [Optional]
        LiveVal-->>ScanSvc: validationStatus (ACTIVE / INVALID / UNKNOWN)
        ScanSvc->>Correlator: correlateFingerprint(workspaceId, fingerprint)
        Correlator-->>ScanSvc: matchedSecretId (if present in Vault inventory)
        ScanSvc->>DB: persist SecretFinding & Occurrences
    end

    ScanSvc-->>API: RepositoryScanSummary (Findings count, severities, run time)
    API-->>DevSecOps: 200 OK (Clean SARIF / JSON with zero plaintext)

    opt Finding Requires Immediate Remediation
        DevSecOps->>API: POST /api/v1/workspaces/{wsId}/findings/{id}/remediate (ROTATE_SECRET)
        API->>RotSvc: triggerEmergencyRotation(workspaceId, matchedSecretId)
        RotSvc-->>API: RotationJob Started (Dual-credential grace period)
        API-->>DevSecOps: 200 OK (Remediation job created)
    end
```

---

## 4. Multi-Tenancy & Workspace Scoping

All repository entities, scans, findings, allowlists, and remediation jobs are explicitly scoped by `workspace_id`. Cross-workspace scanning or finding retrieval is strictly prohibited at both the database query layer (JPA composite keys and constraints) and the business logic layer (`EffectiveAccessService.requireWorkspacePermission`).

| Permission | Scope | Allowed Actions |
| :--- | :--- | :--- |
| `REPOSITORY_VIEW` | Workspace | View repository list, scan history, masked findings, "Why Exposed" |
| `REPOSITORY_SCAN` | Workspace | Initiate scans, upload archives for scanning |
| `REPOSITORY_MANAGE` | Workspace | Connect repositories, update policies, configure Webhooks |
| `REPOSITORY_FINDING_MANAGE` | Workspace | Triage findings, mark false positives, manage allowlists |
| `REPOSITORY_REMEDIATE` | Workspace | Trigger emergency secret rotation, credential revocation |
