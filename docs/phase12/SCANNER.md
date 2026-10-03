# Phase 12: Scanning Engine, Sandboxing & Invariants

## 1. Scanner Overview

The SecretVault Scanning Engine orchestrates repository file discovery, Git commit traversal, and archive extraction inside an adversarial-resistant sandboxed execution boundary.

---

## 2. Ingestion Adapters

The engine supports three primary source adapters through `RepositorySourceAdapter`:
1. **Local Filesystem / Working Tree**:
   - Analyzes uncommitted, staged, or working tree files directly from local developer environments or CI runner workspaces.
2. **Git Remote Repository**:
   - Safely clones remotes into ephemeral temporary sandboxes using strict process isolation without shell interpretation.
3. **Zip Archive Ingestion**:
   - Streams in archive files from CI artifact uploads or manual UI submissions.

---

## 3. Sandboxing & Path Traversal Guards

### Canonical Path Containment
All file operations validate that candidate paths are strictly contained within the intended directory hierarchy:
```java
Path canonicalRoot = rootDir.toRealPath();
Path canonicalResolved = resolved.toRealPath();
if (!canonicalResolved.startsWith(canonicalRoot)) {
    throw new SecurityException("Path traversal attempt detected: resolved path escapes sandbox root: " + relativePath);
}
```

### Symlink Escape Defenses
If a directory or file in a repository is a symbolic link, `PathTraversalGuard` resolves the target's canonical location and ensures it cannot escape the sandbox boundary into sensitive system areas (e.g., `/etc`, `/var`, `/home`).

### Null Byte Injection Defense
File paths containing null characters (`\0`) are immediately rejected before passing to OS filesystem syscalls.

---

## 4. Archive Extraction Safeguards (`ArchiveScanner`)

Compressed archives are a common vector for denial of service and directory traversal attacks. SecretVault enforces:

| Vector | Threat | Mitigation | Limit |
| :--- | :--- | :--- | :--- |
| **Zip Slip** | Overwriting files outside the target directory | Canonical target validation via `pathGuard.validateAndResolve` | Strict containment |
| **Zip Bomb** | Uncompressed volume exhaustion (e.g. 42.zip) | Streaming byte counter tracking total uncompressed bytes | Max 500 MB |
| **File Count Bomb** | Inode exhaustion through millions of small files | Counter tracking total entries extracted | Max 10,000 files |
| **Compression Ratio** | Infinite nested expansion | Dynamic ratio monitoring ($Ratio = Bytes_{uncompressed} / Bytes_{compressed}$) | Max ratio 100:1 |

---

## 5. Safe Git Process Execution (`GitProcessExecutor`)

To prevent arbitrary command execution, repository tampering, and environment pollution:
1. **No Shell Invocations**: Commands are invoked strictly via `ProcessBuilder` with explicit `List<String>` argument arrays (never `/bin/sh -c` or `cmd.exe /c`).
2. **Hook Execution Disabled**: Commands set `-c core.hooksPath=/dev/null` to guarantee repository-controlled hooks (such as `post-checkout` or `pre-commit`) are never executed by the scanner.
3. **System Configuration Isolation**: Commands export `GIT_CONFIG_NOSYSTEM=1` and `GIT_TERMINAL_PROMPT=0` to prevent interactive credential prompts or system-level configuration override exploits.
4. **Execution Timeouts**: Every subprocess execution is guarded with strict timeout limits (default 60 seconds) to prevent hanging Git operations.

---

## 6. File Discovery Engine (`FileDiscoveryEngine`)

To balance scanning speed with detection coverage, `FileDiscoveryEngine` filters candidate files:
- **Excluded Directories**: `.git`, `node_modules`, `target`, `build`, `dist`, `vendor`, `.gradle`, `.idea`, `.vscode`, `coverage`, `.next`, `bin`, `obj`.
- **Binary Probing**: Scans the first 1,024 bytes of each file. If more than 1.0% of bytes are null (`0x00`), the file is classified as binary and skipped.
- **File Size Cap**: Individual files exceeding 5 MB are skipped with an audit log to avoid JVM Heap exhaustion.
