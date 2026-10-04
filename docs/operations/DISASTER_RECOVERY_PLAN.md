# SecretVault — Enterprise Disaster Recovery & Business Continuity Plan

## 1. Objectives & SLAs

| Metric | Target SLA | Strategy & Implementation |
| :--- | :--- | :--- |
| **RPO (Recovery Point Objective)** | **<= 15 minutes** | PostgreSQL continuous WAL archiving + automated 5-minute snapshot generation in AWS RDS. S3 backup sync with cross-region replication. |
| **RTO (Recovery Time Objective)** | **<= 60 minutes** | Automated Multi-AZ automatic failover (< 2 min), cross-region ECS Fargate warm standby infrastructure (< 15 min), DNS Route53 failover routing. |
| **Data Durability** | **99.999999999% (11 9's)** | S3 Multi-AZ bucket storage with KMS Server-Side Encryption and Object Lock compliance retention. |

---

## 2. Multi-Region Disaster Recovery Architecture

```
                 Primary Region (us-east-1)                         DR Standby Region (us-west-2)
            ┌───────────────────────────────────┐               ┌───────────────────────────────────┐
            │ Route53 Primary (Health Checked)  │               │ Route53 Failover Standby          │
            │                │                  │               │                │                  │
            │                ▼                  │               │                ▼                  │
            │  Application Load Balancer (ALB)  │               │  Application Load Balancer (ALB)  │
            │                │                  │               │                │                  │
            │                ▼                  │               │                ▼                  │
            │   ECS Fargate Tasks (Min 2)       │               │   ECS Fargate Tasks (Warm Min 1)  │
            │        │               │          │               │        │               │          │
            │        ▼               ▼          │               │        ▼               ▼          │
            │ PostgreSQL Primary   Redis Rep    │ Cross-Region  │ PostgreSQL Replica   Redis Cache  │
            │ (Multi-AZ)           (Multi-AZ)   │ ════════════> │ (Promoted on DR)     (Re-seeded)  │
            │        │                          │ Replication   │                                   │
            │        ▼                          │               │                                   │
            │ AWS KMS (CMK v1) ═══════════════════════════════> │ AWS KMS (Multi-Region Replica)   │
            │        │                          │               │                                   │
            │        ▼                          │               │                                   │
            │ S3 Backup Bucket ═══════════════════════════════> │ S3 DR Backup Bucket (Replica)    │
            └───────────────────────────────────┘               └───────────────────────────────────┘
```

---

## 3. Data Classification & Disaster Recovery Authority

| Data Category | Storage Tier | Authority & Recovery Strategy | RPO / Recovery Mechanism |
| :--- | :--- | :--- | :--- |
| **Secret Payloads & Ciphertext** | PostgreSQL (`secrets`, `secret_versions`) | Authoritative source of truth. Replicated across AZs and backed up to S3. | RPO <= 15m. Restored via PITR or `pg_restore`. |
| **Master Cryptographic KEKs** | AWS KMS (CMK) | AWS KMS Multi-Region Key (`mrk-`) with automated 365-day rotation. | 0 RPO. Instantly decryptable in DR region. |
| **Ephemeral Challenges & Rate Limits** | ElastiCache Redis | Ephemeral security state. Redis re-seeds upon cold start; security controls fail closed. | 0 RPO impact (disposable cache). |
| **Audit Logs & Security Events** | PostgreSQL + S3 Archive | Immutable append-only audit trail. Exported to S3 with Object Lock WORM retention. | RPO <= 5m. WORM compliant. |
| **Infrastructure State** | Terraform State S3 Backend | S3 versioned bucket with DynamoDB state locking. | Continuous GitOps sync. |

---

## 4. Disaster Recovery Testing & Drills

1. **Bi-Annual DR Drills:** Scheduled simulation of primary region failure.
2. **Automated Restore Integrity Testing:** Weekly automated script `infrastructure/scripts/test_db_restore.sh` validating SHA-256 backup restoration into isolated staging environment.
3. **Failover Verification Protocol:** Validation of zero-downtime rotation resumption and client SDK reconnects after failover.
