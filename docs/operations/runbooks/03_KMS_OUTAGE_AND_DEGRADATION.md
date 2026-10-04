# SecretVault Operational Runbook 03: AWS KMS Outage & Cryptographic Degradation

## Incident Summary
Triggered when AWS KMS returns 5xx errors, throttles requests (`ThrottlingException`), or encounters regional network partition preventing DEK encryption or decryption.

---

### Step-by-Step Response Procedure

1. **Detect**: Alert `SecretVaultKmsOutage` or CloudWatch metric alarm on KMS decrypt errors > 5.
2. **Impact Assessment**:
   - New secret encryptions and decrypts fail closed with `ApiException(KMS_UNAVAILABLE)`.
   - Existing in-memory cached secrets in client SDKs remain valid until cache TTL expires.
3. **Check AWS Service Health Dashboard**: Verify if AWS KMS regional service degradation is present in active region (`us-east-1`).
4. **Rate Limit & Throttling Mitigation**:
   - Check if account KMS request quotas were exceeded. Request immediate AWS support quota increase if throttling.
5. **Cross-Region DR Failover (if region outage)**:
   - Follow Runbook 12 (`12_AWS_REGION_OUTAGE_AND_DR_CUTOVER.md`) to switch traffic to DR region (`us-west-2`) using multi-region replica KMS key.
6. **Recovery & Cache Warmup**:
   - Once AWS KMS recovers, verify health endpoint:
     ```bash
     curl https://vault.internal.net/api/v1/system/health-score
     ```
7. **Audit**: Review `KmsException` count in CloudWatch logs to verify complete recovery.
