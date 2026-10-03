# Phase 12: STRIDE Threat Model

## 1. Overview

This document presents the STRIDE threat model evaluating the repository scanning subsystem.

---

## 2. STRIDE Assessment Matrix

| STRIDE Category | Threat Scenario | Impact | Phase 12 Mitigation |
| :--- | :--- | :--- | :--- |
| **Spoofing** | Attacker spoofs Git commit author or scanner identity | Low | Findings record commit SHA and GPG signature verification status where available. Scans authenticated via workspace JWT. |
| **Tampering** | Malicious repo contains modified Git hooks (`.git/hooks/post-checkout`) to execute code on scanner | Critical | Commands explicitly supply `-c core.hooksPath=/dev/null` and `GIT_CONFIG_NOSYSTEM=1`. Subprocesses cannot execute repo-controlled hooks. |
| **Repudiation** | User denies marking finding as false positive or ignoring leak | Medium | `AuditService` records immutable tamper-evident logs for every finding status change and remediation action. |
| **Information Disclosure** | Scanner logs or persists unmasked credentials, exposing them to monitoring systems | Critical | Scanner computes SHA-256 fingerprint and non-reversible masked strings. Plaintext is never persisted in database or logs. |
| **Denial of Service** | Scanned archive contains ZipBomb (decompression bomb) or ReDoS payload | High | `ArchiveScanner` enforces maximum file count (10,000) and max uncompressed size (500 MB). Regex patterns are verified ReDoS-safe. |
| **Elevation of Privilege** | Path traversal / ZipSlip writes files outside sandbox directory into system path | Critical | `PathTraversalGuard` validates canonical real paths; rejects null bytes and directory escapes (`../`). |
