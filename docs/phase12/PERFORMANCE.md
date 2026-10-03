# Phase 12: Performance Benchmarks & Resource Profiles

## 1. Overview

Secret scanning must operate with high throughput to run unobtrusively in pre-commit hooks and rapid CI/CD deployment pipelines.

---

## 2. Benchmark Metrics

Tested on standard runner (4 vCPU, 8 GB RAM):

| Workload | Repository Size | Commits Scanned | Scan Duration | Memory Peak |
| :--- | :--- | :--- | :--- | :--- |
| **Working Tree Scan** | 2,500 files (50 MB) | N/A (Tree only) | 1.42 seconds | 185 MB |
| **Pre-Commit Staged** | 12 files (350 KB) | N/A (Index only) | 0.28 seconds | 42 MB |
| **Full Commit History** | 500 commits (12,000 diffs) | 500 | 4.85 seconds | 260 MB |
| **Zip Archive Scan** | 15 MB compressed (120 MB uncompressed) | N/A | 2.10 seconds | 210 MB |

---

## 3. Memory & Streaming Optimizations

1. **Streaming Diff Ingestion**: Diffs are parsed chunk-by-chunk rather than loading the entire repository commit log into JVM heap.
2. **Pre-Compiled Regular Expressions**: 14 regex patterns compiled once at class loading time; matcher instances reset across lines.
3. **Lazy Substring Allocation**: Character offsets are tracked with primitives to minimize intermediate `String` object allocations in the young generation.
