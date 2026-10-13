-- V25: Webhook Hardening, Secret Rotation Transition and Worker Lease Tracking

-- 1. Support dual-secret rotation transition on webhook endpoints
ALTER TABLE webhook_endpoints
    ADD COLUMN IF NOT EXISTS previous_signing_secret_encrypted TEXT,
    ADD COLUMN IF NOT EXISTS secret_rotated_at TIMESTAMP WITH TIME ZONE;

-- 2. Support worker lease tracking, atomic multi-worker claiming, and self-contained delivery replay
ALTER TABLE webhook_deliveries
    ADD COLUMN IF NOT EXISTS payload_json TEXT,
    ADD COLUMN IF NOT EXISTS worker_id VARCHAR(128),
    ADD COLUMN IF NOT EXISTS leased_until TIMESTAMP WITH TIME ZONE;

-- 3. Dedicated indexes for due delivery polling and stale lease recovery
CREATE INDEX IF NOT EXISTS idx_webhook_del_status_retry ON webhook_deliveries(status, next_retry_at);
CREATE INDEX IF NOT EXISTS idx_webhook_del_lease ON webhook_deliveries(status, leased_until);
