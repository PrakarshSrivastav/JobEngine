-- Enum declaration order matters: ORDER BY priority DESC puts CRITICAL first.
CREATE TYPE job_status AS ENUM ('PENDING', 'QUEUED', 'RUNNING', 'SUCCESS', 'FAILED', 'RETRYING', 'DEAD', 'CANCELLED');
CREATE TYPE job_priority AS ENUM ('LOW', 'NORMAL', 'HIGH', 'CRITICAL');

CREATE TABLE jobs (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    type          VARCHAR(100) NOT NULL,
    status        job_status   NOT NULL DEFAULT 'PENDING',
    priority      job_priority NOT NULL DEFAULT 'NORMAL',
    payload       JSONB,
    result        JSONB,
    error_message TEXT,
    retry_count   INTEGER      NOT NULL DEFAULT 0,
    max_retries   INTEGER      NOT NULL DEFAULT 3,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    scheduled_at  TIMESTAMPTZ,
    started_at    TIMESTAMPTZ,
    completed_at  TIMESTAMPTZ,
    heartbeat_at  TIMESTAMPTZ,
    worker_id     VARCHAR(100)
);

-- Serves the claim query: only claimable rows, already in dispatch order.
CREATE INDEX idx_jobs_claimable ON jobs (priority DESC, created_at)
    WHERE status IN ('PENDING', 'RETRYING');

-- Serves the lease reaper.
CREATE INDEX idx_jobs_in_flight ON jobs (heartbeat_at)
    WHERE status IN ('QUEUED', 'RUNNING');

CREATE INDEX idx_jobs_status_created ON jobs (status, created_at DESC);

CREATE TABLE job_events (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id      UUID        NOT NULL REFERENCES jobs (id) ON DELETE CASCADE,
    from_status job_status,
    to_status   job_status  NOT NULL,
    message     TEXT,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_job_events_job ON job_events (job_id, occurred_at);

CREATE TABLE dead_letter_queue (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id         UUID        NOT NULL UNIQUE REFERENCES jobs (id) ON DELETE CASCADE,
    failure_reason TEXT,
    final_error    TEXT,
    moved_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
