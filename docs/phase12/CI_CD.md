# Phase 12: CI/CD Pipeline Guardrails & SARIF Integration

## 1. Overview

SecretVault seamlessly integrates into enterprise continuous integration pipelines to act as an automated quality gate that prevents build artifacts from containing leaked credentials.

---

## 2. Standardized SARIF v2.1.0 Output

SecretVault produces OASIS Standard Static Analysis Results Interchange Format (SARIF) v2.1.0 JSON payloads via `SarifFormatter`.

### Output Structure
```json
{
  "$schema": "https://raw.githubusercontent.com/oasis-tcs/sarif-spec/master/Schemata/sarif-schema-2.1.0.json",
  "version": "2.1.0",
  "runs": [
    {
      "tool": {
        "driver": {
          "name": "SecretVault Scanner",
          "version": "1.0.0",
          "informationUri": "https://secretvault.io",
          "rules": [
            {
              "id": "AWS_ACCESS_KEY",
              "name": "AWS Access Key",
              "shortDescription": { "text": "Detected AWS_ACCESS_KEY secret credential" },
              "defaultConfiguration": { "level": "error" }
            }
          ]
        }
      },
      "results": [
        {
          "ruleId": "AWS_ACCESS_KEY",
          "message": { "text": "Potential secret exposure: AWS_ACCESS_KEY (AKIA************MP12)" },
          "locations": [
            {
              "physicalLocation": {
                "artifactLocation": { "uri": "config/aws.env" },
                "region": { "startLine": 12, "startColumn": 1 }
              }
            }
          ]
        }
      ]
    }
  ]
}
```

---

## 3. Exit Codes Specification

The CLI uses POSIX-standard deterministic exit codes:
- **0**: Scan completed successfully, no policy-violating secrets found.
- **1**: Critical or high severity secrets detected exceeding `--fail-on` threshold.
- **2**: Scan configuration error (invalid parameters, missing arguments).
- **3**: Network / API communication failure.
- **4**: Authentication failure (expired token or invalid workspace credentials).
- **5**: Internal sandbox / scan engine runtime exception.

---

## 4. Pipeline Examples

### GitLab CI (`.gitlab-ci.yml`)
```yaml
secretvault_scan:
  stage: test
  image: eclipse-temurin:21-jre-alpine
  script:
    - secretvault scan --fail-on=HIGH --sarif=gl-secret-detection-report.json
  artifacts:
    reports:
      secret_detection: gl-secret-detection-report.json
```
