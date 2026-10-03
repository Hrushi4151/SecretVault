# Automation Policy Engine & Condition DSL

## 1. Overview

The SecretVault Automation Policy Engine enables deterministic, automated response rules triggered by domain events. Policies evaluate conditions against event fields and execute pre-configured security actions.

## 2. Sandboxed Abstract Syntax Tree (AST) Condition DSL

The engine uses a deterministic, strictly sandboxed condition evaluator (`ConditionAstEvaluator`). Arbitrary code execution (SpEL, Groovy, JS eval, shell) is strictly prohibited.

### 2.1 Supported Operators
- Comparison: `EQUALS`, `NOT_EQUALS`, `CONTAINS`, `STARTS_WITH`, `IN`, `NOT_IN`
- Numeric: `GREATER_THAN`, `GREATER_THAN_OR_EQUAL`, `LESS_THAN`, `LESS_THAN_OR_EQUAL`
- Logical Composites: `AND`, `OR`, `NOT`

### 2.2 Allowed Fields
- `eventType`, `severity`, `source`
- `workspaceId`, `projectId`, `environmentId`, `secretId`
- `metadata.key`, `metadata.status`, `metadata.attemptCount`
- Numerical metadata attributes (e.g. `metadata.rotationAgeDays`, `metadata.leaseAgeMinutes`)

### 2.3 Example Condition JSON

```json
{
  "operator": "AND",
  "children": [
    {
      "field": "eventType",
      "operator": "EQUALS",
      "value": "ROTATION_FAILED"
    },
    {
      "operator": "OR",
      "children": [
        {
          "field": "metadata.attemptCount",
          "operator": "GREATER_THAN",
          "value": 3
        },
        {
          "field": "severity",
          "operator": "IN",
          "values": ["HIGH", "CRITICAL"]
        }
      ]
    }
  ]
}
```

## 3. Dry-Run & Simulation Engine

Before deploying an automation policy to production, operators can run simulations via:
`POST /api/v1/workspaces/{workspaceId}/automation-policies/simulate`

The simulator evaluates the policy against past or synthesized domain events, returning:
- Whether conditions matched
- Which actions would have fired
- Required approval flags
- Execution duration estimate
Without executing any mutating side effects.
