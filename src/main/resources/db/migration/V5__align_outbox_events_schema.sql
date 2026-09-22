ALTER TABLE outbox_events ADD COLUMN IF NOT EXISTS aggregate_type VARCHAR(50) NOT NULL DEFAULT 'JOB';
ALTER TABLE outbox_events ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'PENDING';
ALTER TABLE outbox_events ADD COLUMN IF NOT EXISTS sent_at TIMESTAMP WITH TIME ZONE;

-- Sync any published records
UPDATE outbox_events SET sent_at = published_at, status = 'SENT' WHERE published_at IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_outbox_events_pending ON outbox_events(status, created_at ASC) WHERE status = 'PENDING';
