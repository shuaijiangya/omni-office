CREATE TABLE omni_generation_job (
    job_id                  UUID PRIMARY KEY,
    tenant_id               VARCHAR(64) NOT NULL,
    principal_id            VARCHAR(64) NOT NULL,
    correlation_id          VARCHAR(128) NOT NULL,
    idempotency_key         VARCHAR(128),
    request_sha256          CHAR(64) NOT NULL,
    mode                    VARCHAR(32) NOT NULL,
    request_json            JSONB NOT NULL,
    status                  VARCHAR(32) NOT NULL,
    attempt_count           INTEGER NOT NULL DEFAULT 0,
    max_attempts            INTEGER NOT NULL,
    error_code              VARCHAR(128),
    error_message           VARCHAR(1000),
    created_at              TIMESTAMPTZ NOT NULL,
    started_at              TIMESTAMPTZ,
    completed_at            TIMESTAMPTZ,
    updated_at              TIMESTAMPTZ NOT NULL,
    version                 BIGINT NOT NULL,
    lease_owner             VARCHAR(128),
    lease_until             TIMESTAMPTZ,
    terminal_event_id       VARCHAR(128),
    terminal_event_queued_at TIMESTAMPTZ,
    artifacts_json          JSONB NOT NULL DEFAULT '[]'::jsonb,
    CONSTRAINT ck_omni_generation_attempts CHECK (
        attempt_count >= 0 AND max_attempts BETWEEN 1 AND 3
    )
);

CREATE UNIQUE INDEX uq_omni_generation_idempotency
    ON omni_generation_job (tenant_id, principal_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

CREATE INDEX ix_omni_generation_claim
    ON omni_generation_job (tenant_id, status, lease_until, created_at);

CREATE INDEX ix_omni_generation_list
    ON omni_generation_job (tenant_id, created_at DESC);
