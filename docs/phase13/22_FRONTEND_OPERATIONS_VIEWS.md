# Frontend Operations Views Architecture

## 1. Overview

Phase 13 integrates 5 dedicated views and modals into the SecretVault SPA (`frontend/`):

1. **`EventCenterView.jsx` (`events` tab):**
   - Outbox stream table with status badges (`PENDING`, `PROCESSED`, `FAILED`, `DEAD_LETTER`).
   - JSON payload inspector with syntax highlighting and zero-leak confirmation.
   - Replay Request Modal with scope filters and side-effect toggling.
2. **`AutomationCenterView.jsx` (`automation` tab):**
   - Policy management table with enabled toggles and priority ranking.
   - Four-Eyes Approvals Queue with 1-click Approve/Reject modals and justification text.
   - Execution audit history with execution duration (ms) and error stacks.
   - Policy Simulator Modal allowing dry-run evaluation of policies against test payloads.
3. **`WebhookCenterView.jsx` (`webhooks` tab):**
   - Registered webhook endpoints list with secret prefix and health indicators.
   - SSRF warning banner reminding operators that private IPs and cloud metadata are strictly blocked.
   - Delivery log drawer showing HTTP response codes, latency, and 1-click replay triggers.
4. **`SecurityOperationsView.jsx` (`incidents` tab):**
   - Incident lifecycle kanban and triage queue (`OPEN` to `CLOSED`).
   - Incident declaration modal with severity and category tags.
   - Incident drawer with correlated domain event timeline and status transition controls.
5. **`NotificationCenterModal.jsx`:**
   - Slide-over notification bell drawer accessible from the global top navbar (`AppShell.jsx`).
   - Unread badge counter, Mark All as Read button, and channel routing preferences tab.

## 2. Testing & Quality Metrics
- 100% component test coverage in `Phase13Views.test.jsx`.
- Clean Vitest execution across 11 test suites (56 tests total).
- Clean production bundle build via Vite (`npm run build`).
