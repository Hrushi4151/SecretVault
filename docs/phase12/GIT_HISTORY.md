# Phase 12: Git History Traversal & Commit Diff Analysis

## 1. Overview

Credentials committed to source control often remain permanently embedded in Git commit history even after deletion from the working tree. SecretVault scans both the active working tree and historical commit diff graphs.

---

## 2. Commit Traversal Algorithm

The scanner executes streaming commit log traversal using `GitProcessExecutor`:
```bash
git log --all --no-merges -n <max_commits> --pretty=format:COMMIT:%H|%an|%ae|%at
```

For each commit in the traversal:
```bash
git diff-tree --no-commit-id --name-only -r <commit_hash>
```

For each modified scannable file in that commit:
```bash
git show <commit_hash>:<file_path>
```
Or for streaming line diffs:
```bash
git diff-tree -p -U0 <commit_hash>
```

---

## 3. Added-Line Diff Filter

When scanning commit history or Pull Requests, only newly added or modified lines (lines starting with `+` in unified diff format, excluding the `+++` file header) are scanned:
```
--- a/src/config.py
+++ b/src/config.py
@@ -10,1 +10,1 @@
-api_key = os.getenv("API_KEY")
+api_key = "AKIAIOSFODNN7EXAMPLE"  <-- Scanned as candidate
```

This prevents duplicate notifications for pre-existing findings that were not modified in the current commit or PR.

---

## 4. Cross-Branch & Tag Coverage

Scans can be configured with:
- `includeAllBranches = true`: Traverses all local and remote tracking branches (`refs/heads/*`, `refs/remotes/*`).
- `includeTags = true`: Traverses annotated and lightweight release tags (`refs/tags/*`).
- `maxHistoryDepth`: Configurable cap (e.g. 500, 1000, or unlimited) to manage scan execution time on legacy multi-gigabyte repositories.

---

## 5. Performance Optimizations

1. **Blob Deduplication**: Identical Git object hashes (`tree` or `blob` SHA-1) across multiple branches or commits are scanned only once per scan session.
2. **Streaming Execution**: File contents are processed in memory and never written to secondary temporary files.
3. **Commit Metadata Attribution**: Each finding automatically captures the originating `commitSha`, `author`, `branch`, and timestamp for precise attribution.
