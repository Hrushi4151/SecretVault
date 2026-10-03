# SecretVault SDK — Serverless Execution (AWS Lambda, Cloud Run, Short-lived Jobs)

## Serverless Operational Guidelines

1. **Disable Background Schedulers**: Set `secretvault.refresh.enabled=false` to prevent background polling threads from blocking container freeze.
2. **Execution-Scoped Caching**: Secrets cached during invocation warm-up remain valid across warm starts within their 60s TTL.
3. **Fail-Closed Fast Failure**: Set connect and read timeouts low (e.g. 2s) to prevent Lambda invocation timeouts.
