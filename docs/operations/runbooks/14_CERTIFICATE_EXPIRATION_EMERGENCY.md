# SecretVault Operational Runbook 14: TLS Certificate Expiration Emergency

## Incident Summary
Triggered when an external TLS certificate expires or fails automated ACME/ACM renewal, causing browser TLS warnings or API connection rejections.

---

### Step-by-Step Response Procedure

1. **Detect**: Alert `CertificateExpiryWarning` (< 14 days) or client SSL handshake failure.
2. **Check AWS ACM Certificate Status**:
   ```bash
   aws acm list-certificates --certificate-statuses PENDING_VALIDATION ISSUED
   ```
3. **Emergency Certificate Re-issuance**:
   - Request immediate certificate via ACM:
     ```bash
     aws acm request-certificate \
       --domain-name "vault.secretvault.dev" \
       --validation-method DNS \
       --subject-alternative-names "*.vault.secretvault.dev"
     ```
4. **Complete DNS Validation**: Add required CNAME records to Route53 / DNS zone.
5. **Attach Certificate to ALB HTTPS Listener**:
   ```bash
   aws elbv2 modify-listener \
     --listener-arn "${ALB_HTTPS_LISTENER_ARN}" \
     --certificates CertificateArn="${NEW_CERT_ARN}"
   ```
6. **Verify TLS Handshake**:
   ```bash
   openssl s_client -connect vault.secretvault.dev:443 -servername vault.secretvault.dev < /dev/null | openssl x509 -noout -dates
   ```
