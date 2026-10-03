# Phase 12: Repository Policies, Allowlisting & Path Rules

## 1. Overview

Repository Policies allow security teams to define tenant-wide or per-repository rules governing scan behavior, sensitivity thresholds, path exclusions, and triage allowlists.

---

## 2. Policy Schema (`RepositoryPolicy`)

| Configuration Parameter | Type | Default | Description |
| :--- | :--- | :--- | :--- |
| `minEntropyThreshold` | Double | `4.5` | Minimum Shannon entropy for generic string classification |
| `failOnSeverity` | `RepoFindingSeverity` | `HIGH` | Minimum severity triggering CI/CD build failure |
| `blockPullRequestsOnLeak` | Boolean | `true` | Whether PR commit checks fail on leak detection |
| `scanGitHistory` | Boolean | `true` | Whether full commit diff history is traversed |
| `maxHistoryDepth` | Integer | `500` | Max number of commits to scan |
| `excludedPathsJson` | String (JSON Array) | `[...defaults]` | Glob patterns for ignored paths (e.g. `docs/**`, `*.md`) |
| `customPatternsJson` | String (JSON Array) | `[]` | Organization-specific regex patterns and custom detectors |

---

## 3. Finding Allowlists (`FindingAllowlist`)

Legitimate test keys, sample credentials, or accepted risks can be allowlisted with cryptographic precision without disabling the detector for the rest of the repository.

Allowlists evaluate matches against:
1. `fingerprint`: Exact SHA-256 hash match of the token.
2. `detectorType`: Specific detector (e.g. `AWS_ACCESS_KEY`).
3. `filePattern`: Optional glob filter restricting the allowlist to specific files (e.g. `tests/fixtures/**`).
4. `expiresAt`: Automatic expiration timestamp forcing periodic re-review.
5. `reason`: Mandatory justification for compliance audits.
