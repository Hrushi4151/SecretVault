# Automation Loop Detection & Execution Budgets

## 1. Threat: Infinite Automation Cascades

Because automation actions (e.g. `TRIGGER_ROTATION`, `REVOKE_LEASE`) emit subsequent domain events (`ROTATION_TRIGGERED`, `LEASE_REVOKED`), circular dependencies between policies could cause unbounded execution loops, resource exhaustion, or cascading downtime.

## 2. Causation Chain Depth Tracking

Every domain event carries:
- `correlationId`: Traces the originating user or schedule session.
- `causationId`: Points to the immediate parent event that triggered this event.

The `AutomationLoopDetector` inspects the causation ancestry:
1. When an event is evaluated, the depth counter increases:
   $$\text{depth} = \text{depth}(\text{causationEvent}) + 1$$
2. If depth exceeds the configured threshold (`maxRecursionDepth = 5`), execution is immediately aborted.
3. An `AUTOMATION_LOOP_DETECTED` security finding is generated.
4. The offending policy is placed into a temporary circuit-breaker state (`PAUSED`).

## 3. Per-Policy Execution Rate Limiting

In addition to depth tripwires, policies enforce execution budgets:
- Window: Sliding 60-second window.
- Limit: Maximum 10 executions per policy per minute.
- Excess events are logged as `SKIPPED` in `automation_executions` and routed to the administrator alert queue.
