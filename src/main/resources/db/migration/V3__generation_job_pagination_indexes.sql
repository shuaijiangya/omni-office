CREATE INDEX ix_omni_generation_page
    ON omni_generation_job (tenant_id, created_at DESC, job_id DESC);

CREATE INDEX ix_omni_generation_status_page
    ON omni_generation_job (tenant_id, status, created_at DESC, job_id DESC);
