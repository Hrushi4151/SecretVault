# Secret Health & Risk Intelligence

## 1. Multi-Factor Health Scoring

SecretVault evaluates the posture of each secret via `SecretHealthEvaluator`. Health is scored on an integer scale from **0 (Critical Risk)** to **100 (Optimal)**.

## 2. Scoring Formula Breakdown

Starting baseline score: 100 points.

| Risk Factor | Penalty Deduction | Explanation |
| :--- | :--- | :--- |
| **Rotation Overdue** | -30 points | Secret age exceeds rotation policy max interval or last rotation failed. |
| **High Active Leases** | -15 points | More than 20 concurrent active leases without dynamic cleanup. |
| **High Reveal Frequency** | -20 points | Secret revealed more than 10 times in the past 24 hours. |
| **Stale Workload Consumers**| -25 points | Registered consumers have missed heartbeats for over 15 minutes. |
| **Provider Drift Detected** | -20 points | External provider value diverged from SecretVault canonical state. |

## 3. Explainable Risk Factors

The API returns both the overall score and the detailed breakdown of deductions:

```json
{
  "secretId": "e12f9bf2-72ee-449e-8be1-f67ce0ffea6a",
  "secretKey": "STRIPE_API_KEY",
  "healthScore": 45,
  "status": "DEGRADED",
  "factors": [
    {
      "name": "ROTATION_OVERDUE",
      "severity": "HIGH",
      "deduction": 30,
      "message": "Secret has not been rotated in 124 days (policy limit: 90 days)"
    },
    {
      "name": "STALE_CONSUMERS",
      "severity": "MEDIUM",
      "deduction": 25,
      "message": "2 of 5 registered consumers are currently stale"
    }
  ],
  "recommendations": [
    "Trigger an immediate rotation via 'secretvault rotation trigger'",
    "Investigate billing-worker-2 heartbeat failures"
  ]
}
```
