# Automated Compromise Remediation Playbook

## 1. Trigger Scenario: Secret Leak or Compromise

When a secret key is identified in a public git repository, exposed log stream, or reported by an engineer, an operator or automated detection rule issues a `SECRET_COMPROMISED` event.

## 2. Automated Step-by-Step Playbook

```
1. Ingest SECRET_COMPROMISED
         │
         ▼
2. Create CRITICAL Security Incident (SEC-INC-XXXX)
         │
         ▼
3. Immediate Blast Radius Containment:
   • Force-revoke all active dynamic leases on the secret
   • Temporarily suspend dependent machine credentials
         │
         ▼
4. Dependency & Consumer Impact Analysis:
   • Inspect registered consumers in SecretConsumerRegistry
   • Partition consumers: dynamic refresh vs static restart
         │
         ▼
5. Execute Emergency Rotation:
   • Trigger RotationService with strategy=EMERGENCY
   • Generate new cryptographic material
   • Validate new secret against downstream target
   • Stage and activate new version (V_new)
         │
         ▼
6. Consumer Migration:
   • Issue reload signals to dynamic consumers
   • Alert operators for workloads requiring rolling restart
         │
         ▼
7. Invalidate Compromised Material:
   • Immediately revoke compromised version (V_compromised)
   • Update incident status to REMEDIATION -> RESOLVED
```

## 3. Guarantees

- **No Ghost Windows:** Active leases tied to the old credential are invalidated in memory and revoked at the database/provider level.
- **Audited Execution:** Every phase transition records an explicit domain event and audit ledger entry with causation linkage.
