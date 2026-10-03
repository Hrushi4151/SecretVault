# Phase 12: Rotation Engine Integration & Compromise Cascade

## 1. Overview

SecretVault uniquely unifies repository secret scanning with its built-in enterprise rotation engine. Leaked credentials can trigger automated, zero-downtime key rotation directly from the repository finding interface or CI pipeline.

---

## 2. Integration Architecture

```mermaid
sequenceDiagram
    participant RepoScanner as Repository Scanner
    participant Correlator as SecretInventoryCorrelator
    participant Remediator as FindingRemediationService
    participant RotationSvc as RotationService
    participant StateMachine as Rotation State Machine
    participant Provider as Cloud / DB Provider

    RepoScanner->>Correlator: correlateByFingerprint(sha256Hash)
    Correlator-->>RepoScanner: matchedSecretId
    RepoScanner->>Remediator: remediateFinding(ROTATE_SECRET)
    Remediator->>RotationSvc: triggerRotation(EMERGENCY, secretId)
    
    RotationSvc->>StateMachine: transitionTo(TRIGGERED)
    StateMachine->>Provider: generateAndDeployNewSecret()
    Provider-->>StateMachine: verifiedActive
    StateMachine->>StateMachine: enterGracePeriod(DUAL_CREDENTIAL)
    
    Note over StateMachine,Provider: Applications receive new credential via SDK heartbeat
    
    StateMachine->>Provider: revokeObsoleteLeakedSecret()
    StateMachine->>RotationSvc: transitionTo(COMPLETED)
    RotationSvc-->>Remediator: Remediation Completed (Status: ROTATED)
```

---

## 3. Zero-Downtime Dual-Credential Grace Period

When emergency rotation is triggered due to a public leak:
1. The new credential is generated and activated on the provider alongside the compromised credential.
2. Applications querying SecretVault via SDK (`@VaultSecret`, `secretvault-sdk-core`) or runtime agents automatically receive the new credential on their next heartbeat interval.
3. The leaked credential is monitored and revoked only after all active consumers have reported successful migration (or upon expiration of the emergency grace period timer).
4. The repository finding is updated to `REMEDIATED` with an immutable entry in the workspace audit trail.
