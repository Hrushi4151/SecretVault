# Unified Notification Engine & Channel Preferences

## 1. Overview

The SecretVault Notification Engine provides alerting across multiple channels:
- In-App Notification Center (Bell drawer in UI)
- Outbound Webhooks (Slack/Teams/PagerDuty bridges)
- Email Notifications (SMTP / transactional email providers)

## 2. Notification Data Model (`notifications`)

```sql
CREATE TABLE notifications (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL,
    recipient_id UUID,
    severity VARCHAR(20) NOT NULL,
    title VARCHAR(255) NOT NULL,
    message TEXT NOT NULL,
    event_id UUID,
    action_url VARCHAR(512),
    channel VARCHAR(50) NOT NULL DEFAULT 'IN_APP',
    status VARCHAR(30) NOT NULL DEFAULT 'UNREAD',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    read_at TIMESTAMP WITH TIME ZONE,
    acknowledged_at TIMESTAMP WITH TIME ZONE,
    expires_at TIMESTAMP WITH TIME ZONE
);
```

## 3. User & Workspace Preferences (`notification_preferences`)

Operators can configure custom routing preferences:
- Minimum severity threshold (`INFO`, `LOW`, `MEDIUM`, `HIGH`, `CRITICAL`).
- Channel toggles (`channelInApp`, `channelWebhook`, `channelEmail`).
- Muted event types (e.g. mute routine `ROTATION_COMPLETED` while retaining `ROTATION_FAILED`).
- Quiet hours (start and end times where non-critical alerts are suppressed).
