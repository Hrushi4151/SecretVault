# CLI Reference Manual: Phase 12 Commands

The `secretvault` CLI provides first-class commands for secret rotation, leases, consumers, and emergency compromise response.

---

## 1. Rotation Commands (`secretvault rotation`)

### List Policies
```bash
secretvault rotation policy list --workspace-id <workspaceId>
```

### Create Policy
```bash
secretvault rotation policy create \
  --workspace-id <workspaceId> \
  --project-id <projectId> \
  --env-id <envId> \
  --secret-id <secretId> \
  --strategy SCHEDULED \
  --secret-type PASSWORD \
  --interval-seconds 2592000 \
  --grace-period-seconds 1800 \
  --enabled
```

### Trigger Rotation Job
```bash
secretvault rotation trigger \
  --workspace-id <workspaceId> \
  --secret-id <secretId> \
  --strategy MANUAL \
  --reason "Routine scheduled rotation"
```

### Rollback Rotation Job
```bash
secretvault rotation rollback \
  --workspace-id <workspaceId> \
  --job-id <jobId> \
  --target-version 2 \
  --reason "Compatibility issues in v3"
```

### Calculate Impact
```bash
secretvault rotation impact \
  --workspace-id <workspaceId> \
  --secret-id <secretId>
```

---

## 2. Lease Commands (`secretvault lease`)

### Issue Lease
```bash
secretvault lease create \
  --workspace-id <workspaceId> \
  --secret-id <secretId> \
  --ttl 3600 \
  --max-lifetime 86400
```

### List Leases
```bash
secretvault lease list --workspace-id <workspaceId> --status ACTIVE
```

### Renew Lease
```bash
secretvault lease renew \
  --workspace-id <workspaceId> \
  --lease-id <leaseId> \
  --extend-seconds 3600
```

### Revoke Lease
```bash
secretvault lease revoke \
  --workspace-id <workspaceId> \
  --lease-id <leaseId>
```

---

## 3. Consumer Commands (`secretvault consumer`)

### Register Consumer
```bash
secretvault consumer register \
  --workspace-id <workspaceId> \
  --project-id <projectId> \
  --env-id <envId> \
  --name "order-worker" \
  --type WORKER \
  --dynamic-refresh
```

### List Consumers
```bash
secretvault consumer list --workspace-id <workspaceId>
```

---

## 4. Secret Lifecycle Shortcuts

### Direct Secret Rotation
```bash
secretvault secret rotate \
  --workspace-id <workspaceId> \
  --secret-id <secretId> \
  --reason "Triggered by deploy pipeline"
```

### Emergency Compromise Remediation
```bash
secretvault secret compromise \
  --workspace-id <workspaceId> \
  --secret-id <secretId> \
  --incident-details "GitHub public repo leak detected" \
  --rotate-immediately \
  --revoke-leases
```
