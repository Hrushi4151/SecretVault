# Phase 12: Adversarial & Chaos Testing

## 1. Adversarial Test Scenarios

The scanning engine has been subjected to rigorous adversarial testing covering known evasion and exploitation techniques:

### Scenario 1: Zip Slip Attack Vector
- **Payload**: `.zip` archive containing file path `../../../../tmp/pwned.sh`.
- **Expected Outcome**: `ArchiveScanner` triggers `SecurityException("Path traversal attempt detected")` before writing bytes. Target directory remains pristine.
- **Verification**: Passed in `SandboxAndTraversalGuardTest.testZipSlipPrevention`.

### Scenario 2: Decompression Bomb (ZipBomb)
- **Payload**: Highly compressed recursive zip with 10,005 nested files.
- **Expected Outcome**: `ArchiveScanner` enforces `MAX_FILES = 10,000` limit and terminates stream extraction safely.
- **Verification**: Passed in `SandboxAndTraversalGuardTest.testZipBombEnforcement`.

### Scenario 3: Symlink Sandbox Escape
- **Payload**: Symlink pointing to `/etc/passwd`.
- **Expected Outcome**: `PathTraversalGuard.validateAndResolve` computes canonical real path and denies access since the target is outside sandbox root.
- **Verification**: Passed in `SandboxAndTraversalGuardTest.testBlockRelativeEscapeAttempts`.

### Scenario 4: ReDoS Backtracking Attack
- **Payload**: Exponentially repeating strings with slight suffix variations (e.g. `AKIA` + 50,000 non-matching characters).
- **Expected Outcome**: Linear scanning time without thread hang or timeout.
- **Verification**: Scan completes in $< 15\text{ms}$.
