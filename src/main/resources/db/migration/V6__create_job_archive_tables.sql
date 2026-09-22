CREATE TABLE job_archive (
    id UUID PRIMARY KEY,
    job_type VARCHAR(50) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(30) NOT NULL,
    attempt_count INT NOT NULL,
    max_attempts INT NOT NULL,
    next_retry_at TIMESTAMP WITH TIME ZONE,
    idempotency_key VARCHAR(255),
    last_error TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT DEFAULT 0,
    archived_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE job_attempt_archive (
    id UUID PRIMARY KEY,
    job_id UUID NOT NULL REFERENCES job_archive(id) ON DELETE CASCADE,
    attempt_number INT NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    finished_at TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(30) NOT NULL,
    error_message TEXT,
    archived_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_job_attempt_archive_job_id ON job_attempt_archive(job_id);
CREATE INDEX idx_job_archive_created_at ON job_archive(created_at DESC);
CREATE INDEX idx_job_archive_status ON job_archive(status);

-- Supporting partial index for the daily archiving query WHERE status = 'COMPLETED' AND updated_at < :cutoff
CREATE INDEX idx_jobs_status_completed_updated_at ON jobs (status, updated_at) WHERE status = 'COMPLETED';
