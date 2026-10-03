# Workload Consumer Registry & Dependency Graph

## 1. Overview

To ensure safe, zero-downtime secret rotation, the control plane must know **which workloads are consuming which secrets**, **which version they currently hold**, and **whether they can dynamically reload secrets without restarting**.

The `SecretConsumerService` maintains a live runtime registry of all applications, background workers, SDK clients, and CLI processes consuming secrets.

---

## 2. Consumer Registration & Heartbeats

### 1. Workload Registration
When a microservice boots up with the SecretVault SDK or Spring Boot Starter:
- It automatically registers as a `SecretConsumer`.
- Declares its `name`, `consumerType`, `instanceId`, `hostname`, `runtimeFramework`, `sdkVersion`, `supportsDynamicRefresh`, and `requiresRestart`.

### 2. Periodic SDK Heartbeat
Workloads periodically send heartbeats to SecretVault:
```json
{
  "currentAcknowledgedVersion": 2,
  "sdkVersion": "1.0.0",
  "runtimeFramework": "Spring Boot 3.3.0"
}
```
- Updates `lastHeartbeatAt`.
- Signals whether the workload has adopted the latest rotated version or is still running on an older version.

### 3. Stale Consumer Detection
An automated background task inspects consumers that have missed heartbeats for over 24 hours:
- Automatically marks them as `STALE`.
- Flags high-risk findings in the Security Center (`SECRET_LEASE_RISK` / `STALE_CONSUMER`).
