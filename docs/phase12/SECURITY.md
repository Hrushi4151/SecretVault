# Phase 12: Security Architecture & Defenses

## 1. Zero-Plaintext Leak Invariant

The scanner's primary directive is to eliminate secondary leaks caused by the scanner itself:
- **No Plaintext Persistence**: Database tables (`secret_findings`, `secret_finding_occurrences`) only store `masked_evidence` and `fingerprint`.
- **No Plaintext Logging**: Logback / SLF4J log lines redact secrets via `SecretFingerprinter.computeMaskedPreview`.
- **No Plaintext in API**: REST DTOs and WebSocket messages return masked previews and metadata only.
- **In-Memory Volatility**: Plaintext strings exist exclusively in local stack frames during detection and are discarded immediately after computing fingerprints.

---

## 2. Process & Subprocess Sandboxing

Subprocess invocations (`git`, `tar`, `unzip`) represent high-risk attack surfaces. SecretVault enforces:
1. `ProcessBuilder` with String arrays (immune to shell metacharacter injection: `;`, `|`, `&&`, `$()`).
2. `core.hooksPath=/dev/null` ensuring Git hooks in scanned repos are disabled.
3. Dedicated scratch directory isolation per scan session (`PathTraversalGuard`).
4. Ephemeral directory cleanup upon scan completion or failure.

---

## 3. ReDoS (Regular Expression Denial of Service) Protections

Regex patterns used for secret detection are verified for polynomial or linear time complexity:
- No catastrophic backtracking (e.g. nested repetition like `(a+)+`).
- Patterns are bounded by specific length qualifiers (`{24,34}`, `{40}`).
- Pre-compiled `Pattern` objects reused as singletons.
