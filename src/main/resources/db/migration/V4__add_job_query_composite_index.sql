-- V4: Add composite index for filtered, sorted, and paginated job queries
CREATE INDEX IF NOT EXISTS idx_jobs_status_type_created
ON jobs (status, job_type, created_at DESC);
