# SecretVault — Enterprise Compliance Evidence Mapping (SOC 2, ISO 27001, GDPR)

## Overview
This document maps SecretVault's cryptographic architecture, access controls, infrastructure configurations, and immutable audit capabilities directly to enterprise security compliance frameworks.

---

### 1. SOC 2 Type II Trust Services Criteria Mapping

| SOC 2 Criteria | Control Description | SecretVault Implementation & Technical Evidence |
| :--- | :--- | :--- |
| **CC6.1 (Logical Access)** | Access to systems and data is restricted to authorized users. | Granular RBAC, workspace tenant isolation, machine identities with OIDC trust policies, MFA/WebAuthn enforcement. Evidence: `access_grants`, `user_sessions`, `AuditAction.ACCESS_GRANT_CREATED`. |
| **CC6.2 (User Registration)** | User credentials and authentication are verified and governed. | Password BCrypt (cost=10), TOTP RFC 6238, WebAuthn FIDO2 passkeys, recovery code hashing. Evidence: `user_mfa`, `webauthn_credentials`. |
| **CC6.3 (Access Revocation)** | Access is modified or revoked timely upon role changes or termination. | JIT temporary access with TTL, Access Review campaigns, session revocation APIs. Evidence: `jit_access_requests`, `access_review_campaigns`. |
| **CC6.6 (Data Protection in Transit)** | Data transmission is protected using strong encryption. | TLS 1.3 enforced on ALB, Redis in-transit encryption, Postgres `sslmode=require`. Evidence: `ELBSecurityPolicy-TLS13`, `nginx.conf` HSTS headers. |
| **CC6.7 (Data Protection at Rest)** | Data at rest is encrypted using approved cryptographic algorithms. | Envelope encryption (AES-256-GCM + 256-bit DEKs), AWS KMS CMK with automated rotation, S3 SSE-KMS. Evidence: `AesGcmEnvelopeEncryptionService.java`, `AwsKmsKeyProvider.java`. |
| **CC7.1 (Vulnerability & Threat Mgmt)** | Infrastructure and code are monitored for security vulnerabilities and threats. | Security Intelligence Engine (15+ detection rules), Git secret scanning, Trivy image scans in CI. Evidence: `SecurityIntelligenceEngine.java`, `.github/workflows/ci.yml`. |
| **CC7.2 (Incident Monitoring)** | Security events are logged, detected, and responded to. | Security incident tracking, centralized CloudWatch alarms, Prometheus alert rules. Evidence: `SecurityIncidentService.java`, `alert_rules.yml`. |
| **CC8.1 (Change Management)** | Changes to infrastructure and code follow tested release controls. | GitHub Actions CI/CD pipelines, expand-migrate-contract DB migrations, Terraform IaC review gates. Evidence: `Flyway`, `V1__init...V19__`, `ci.yml`. |
| **A1.2 (Disaster Recovery)** | Data is backed up and recoverable within SLA targets. | Multi-AZ RDS, automated daily backups to S3 with 30-day retention, PITR verification. Evidence: `backup_postgres.sh`, `restore_postgres.sh`, `DISASTER_RECOVERY_PLAN.md`. |

---

### 2. ISO/IEC 27001:2022 Control Mapping

| ISO 27001 Control | Requirement | Technical Evidence in Repository |
| :--- | :--- | :--- |
| **A.5.15 (Access Control)** | Access rules and rights are managed according to business requirements. | `EffectiveAccessService.java`, `DirectApiAccessControlIntegrationTest.java`. |
| **A.5.17 (Authentication Info)** | Passwords and secret credentials are protected against compromise. | Zero plaintext storage, memory buffer zeroization (`Arrays.fill(bytes, 0)`), secret masking in UI and logs. |
| **A.8.7 (Protection Against Malware)** | Software and dependencies are scanned for malicious components. | Dependency scanning, SBOM generation, minimal non-root Docker images (`backend/Dockerfile`, `frontend/Dockerfile`). |
| **A.8.9 (Configuration Mgmt)** | Standard hardened security configurations are enforced. | `application-prod.yml`, `nginx.conf`, Terraform least-privilege security groups and IAM policies. |
| **A.8.12 (Data Leakage Prevention)** | Controls are implemented to detect and prevent unauthorized data exfiltration. | Secret Reveal Protection with Step-Up auth, Just-In-Time access approval quorums. |
| **A.8.24 (Use of Cryptography)** | Cryptographic keys are managed throughout their lifecycle. | 21-state secret rotation engine, AWS KMS Key Rotation, envelope encryption. |

---

### 3. Automated Compliance Evidence Extraction Script

Compliance officers can generate evidence archives using:
```bash
# Export immutable audit logs for compliance window
curl "https://vault.internal.net/api/v1/audit/export?startDate=2026-01-01&endDate=2026-12-31" \
  -H "Authorization: Bearer ${SV_COMPLIANCE_AUDITOR_TOKEN}" > compliance_audit_trail.json

# Export current system health and SLO report
curl "https://vault.internal.net/api/v1/system/slos" \
  -H "Authorization: Bearer ${SV_COMPLIANCE_AUDITOR_TOKEN}" > compliance_slo_report.json
```
