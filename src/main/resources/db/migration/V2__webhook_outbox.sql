CREATE TABLE omni_webhook_delivery (
    event_id        UUID PRIMARY KEY,
    event_type      VARCHAR(128) NOT NULL,
    tenant_id       VARCHAR(64) NOT NULL,
    webhook_id      VARCHAR(64) NOT NULL,
    job_id          UUID NOT NULL,
    payload_json    JSONB NOT NULL,
    status          VARCHAR(32) NOT NULL,
    attempt_count   INTEGER NOT NULL DEFAULT 0,
    max_attempts    INTEGER NOT NULL,
    response_status INTEGER,
    last_error      VARCHAR(500),
    next_attempt_at TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL,
    delivered_at    TIMESTAMPTZ,
    version         BIGINT NOT NULL,
    lease_owner     VARCHAR(128),
    lease_until     TIMESTAMPTZ,
    CONSTRAINT uq_omni_webhook_event UNIQUE (tenant_id, job_id, event_type),
    CONSTRAINT ck_omni_webhook_attempts CHECK (
        attempt_count >= 0 AND max_attempts BETWEEN 1 AND 20
    )
);

CREATE INDEX ix_omni_webhook_due
    ON omni_webhook_delivery (status, next_attempt_at, lease_until)
    WHERE status IN ('PENDING', 'RETRYING');

CREATE INDEX ix_omni_webhook_tenant_list
    ON omni_webhook_delivery (tenant_id, created_at DESC);
