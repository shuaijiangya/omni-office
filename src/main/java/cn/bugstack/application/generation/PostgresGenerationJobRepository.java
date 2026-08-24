package cn.bugstack.application.generation;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import cn.bugstack.application.generation.webhook.PostgresWebhookDeliveryRepository;
import cn.bugstack.application.generation.webhook.WebhookDeliveryRecord;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.EnumMap;
import java.util.Map;

/** PostgreSQL 生产仓储：依靠唯一索引、乐观版本和 SKIP LOCKED 提供跨实例语义。 */
public final class PostgresGenerationJobRepository implements GenerationJobRepository {

    private static final String COLUMNS = "job_id, tenant_id, principal_id, correlation_id, "
            + "idempotency_key, request_sha256, mode, request_json, status, attempt_count, "
            + "max_attempts, error_code, error_message, current_stage, stage_started_at, deadline_at, draft_id, created_at, started_at, completed_at, "
            + "updated_at, version, lease_owner, lease_until, terminal_event_id, "
            + "terminal_event_queued_at, artifacts_json";

    private final DataSource dataSource;
    private final String tenantId;
    private final ObjectMapper mapper;
    private final JavaType artifactsType;

    /**
     * 创建租户隔离的 PostgreSQL 任务仓储。
     *
     * @param dataSource 共享数据源
     * @param tenantId 当前租户 ID
     */
    public PostgresGenerationJobRepository(DataSource dataSource, String tenantId) {
        if (dataSource == null || tenantId == null || !tenantId.matches("[A-Za-z0-9._-]{1,64}")) {
            throw new IllegalArgumentException("PostgreSQL generation repository configuration is invalid");
        }
        this.dataSource = dataSource;
        this.tenantId = tenantId;
        this.mapper = new ObjectMapper().registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .setSerializationInclusion(JsonInclude.Include.NON_NULL);
        this.artifactsType = mapper.getTypeFactory().constructCollectionType(List.class, GenerationArtifact.class);
    }

    @Override
    public GenerationJobRecord create(GenerationJobRecord record, GenerationQuota quota,
                                      Instant dayStart) {
        if (record != null && record.getCurrentStage() == null) {
            record.setCurrentStage(GenerationStage.QUEUED);
        }
        validate(record);
        if (quota == null || dayStart == null) {
            throw new IllegalArgumentException("generation quota admission is invalid");
        }
        if (!tenantId.equals(record.getTenantId())) throw new IllegalArgumentException("generation tenant mismatch");
        GenerationJobRecord value = copy(record);
        value.setVersion(1L);
        String sql = "INSERT INTO omni_generation_job (" + COLUMNS + ") VALUES ("
                + "?::uuid,?,?,?,?,?,?,?::jsonb,?,?,?,?,?,?,?,?,?::uuid,?,?,?,?,?,?,?,?,?,?::jsonb)";
        try (Connection connection = dataSource.getConnection()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                lockQuotaAdmission(connection);
                enforceQuota(connection, quota, dayStart);
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    bindRecord(statement, value, 1);
                    statement.executeUpdate();
                }
                connection.commit();
                return copy(value);
            } catch (RuntimeException | SQLException e) {
                try { connection.rollback(); } catch (SQLException rollbackFailure) { e.addSuppressed(rollbackFailure); }
                if (e instanceof RuntimeException) throw (RuntimeException) e;
                throw (SQLException) e;
            } finally {
                try { connection.setAutoCommit(originalAutoCommit); } catch (SQLException ignored) { }
            }
        } catch (SQLException e) {
            if ("23505".equals(e.getSQLState())) {
                throw new GenerationJobConflictException("generation job or idempotency key already exists");
            }
            throw databaseFailure("create generation job", e);
        }
    }

    private void lockQuotaAdmission(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT pg_advisory_xact_lock(hashtext(?))")) {
            statement.setString(1, "omni-generation-quota:" + tenantId);
            statement.execute();
        }
    }

    private void enforceQuota(Connection connection, GenerationQuota quota, Instant dayStart)
            throws SQLException {
        String sql = "SELECT count(*) FILTER (WHERE status IN ('QUEUED','RUNNING')) AS active_count,"
                + " count(*) FILTER (WHERE created_at >= ?) AS daily_count"
                + " FROM omni_generation_job WHERE tenant_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setTimestamp(1, timestamp(dayStart));
            statement.setString(2, tenantId);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                if (result.getLong("active_count") >= quota.getMaxActiveJobs()) {
                    throw new GenerationQuotaExceededException("maxActiveJobs",
                            "tenant active generation job quota exceeded");
                }
                if (result.getLong("daily_count") >= quota.getMaxJobsPerDay()) {
                    throw new GenerationQuotaExceededException("maxJobsPerDay",
                            "tenant daily generation job quota exceeded");
                }
            }
        }
    }

    @Override
    public GenerationJobRecord save(GenerationJobRecord record) {
        return update(record, null, false);
    }

    @Override
    public Optional<GenerationJobRecord> claimNext(String workerId, Instant now, Instant leaseUntil) {
        validateLease(workerId, now, leaseUntil);
        String sql = "WITH candidate AS ("
                + " SELECT job_id FROM omni_generation_job"
                + " WHERE tenant_id = ? AND attempt_count < max_attempts"
                + " AND (status = 'QUEUED' OR (status = 'RUNNING'"
                + " AND (lease_until IS NULL OR lease_until <= ?)))"
                + " ORDER BY created_at LIMIT 1 FOR UPDATE SKIP LOCKED"
                + ") UPDATE omni_generation_job job SET status = 'RUNNING',"
                + " attempt_count = job.attempt_count + 1,"
                + " started_at = COALESCE(job.started_at, ?), updated_at = ?,"
                + " lease_owner = ?, lease_until = ?, version = job.version + 1"
                + " FROM candidate WHERE job.job_id = candidate.job_id RETURNING job.*";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, tenantId);
            statement.setTimestamp(2, timestamp(now));
            statement.setTimestamp(3, timestamp(now));
            statement.setTimestamp(4, timestamp(now));
            statement.setString(5, workerId);
            statement.setTimestamp(6, timestamp(leaseUntil));
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(read(result)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw databaseFailure("claim generation job", e);
        }
    }

    @Override
    public Optional<GenerationJobRecord> claimExhausted(String workerId, Instant now, Instant leaseUntil) {
        validateLease(workerId, now, leaseUntil);
        String sql = "WITH candidate AS ("
                + " SELECT job_id FROM omni_generation_job"
                + " WHERE tenant_id = ? AND attempt_count >= max_attempts"
                + " AND (status = 'QUEUED' OR (status = 'RUNNING'"
                + " AND (lease_until IS NULL OR lease_until <= ?)))"
                + " ORDER BY created_at LIMIT 1 FOR UPDATE SKIP LOCKED"
                + ") UPDATE omni_generation_job job SET status = 'RUNNING', updated_at = ?,"
                + " lease_owner = ?, lease_until = ?, version = job.version + 1"
                + " FROM candidate WHERE job.job_id = candidate.job_id RETURNING job.*";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, tenantId);
            statement.setTimestamp(2, timestamp(now));
            statement.setTimestamp(3, timestamp(now));
            statement.setString(4, workerId);
            statement.setTimestamp(5, timestamp(leaseUntil));
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(read(result)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw databaseFailure("claim exhausted generation job", e);
        }
    }

    @Override
    public GenerationJobRecord saveClaimed(GenerationJobRecord record, String workerId) {
        if (workerId == null || workerId.isBlank()) {
            throw new IllegalArgumentException("generation worker id is required");
        }
        return update(record, workerId, true);
    }

    @Override
    public boolean renewLease(String jobId, String workerId, Instant now, Instant leaseUntil) {
        validateLease(workerId, now, leaseUntil);
        String sql = "UPDATE omni_generation_job SET lease_until = ?, updated_at = ?, version = version + 1"
                + " WHERE tenant_id = ? AND job_id = ?::uuid AND status = 'RUNNING' AND lease_owner = ?"
                + " AND lease_until > ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setTimestamp(1, timestamp(leaseUntil));
            statement.setTimestamp(2, timestamp(now));
            statement.setString(3, tenantId);
            statement.setString(4, jobId);
            statement.setString(5, workerId);
            statement.setTimestamp(6, timestamp(now));
            return statement.executeUpdate() == 1;
        } catch (SQLException e) {
            throw databaseFailure("renew generation job lease", e);
        }
    }

    @Override
    public Optional<GenerationJobRecord> find(String jobId) {
        String sql = "SELECT " + COLUMNS + " FROM omni_generation_job"
                + " WHERE tenant_id = ? AND job_id = ?::uuid";
        return selectOne(sql, tenantId, jobId);
    }

    @Override
    public Optional<GenerationJobRecord> findByIdempotencyKey(String principalId, String idempotencyKey) {
        if (principalId == null || idempotencyKey == null) return Optional.empty();
        String sql = "SELECT " + COLUMNS + " FROM omni_generation_job"
                + " WHERE tenant_id = ? AND principal_id = ? AND idempotency_key = ?";
        return selectOne(sql, tenantId, principalId, idempotencyKey);
    }

    @Override
    public List<GenerationJobRecord> list(GenerationJobStatus status, Instant beforeCreatedAt,
                                          String beforeJobId, int limit) {
        return listInternal(null, status, beforeCreatedAt, beforeJobId, limit);
    }

    @Override
    public List<GenerationJobRecord> listForPrincipal(String principalId, GenerationJobStatus status,
                                                      Instant beforeCreatedAt, String beforeJobId,
                                                      int limit) {
        if (principalId == null || !principalId.matches("[A-Za-z0-9._-]{1,64}")) {
            throw new IllegalArgumentException("generation principal id is invalid");
        }
        return listInternal(principalId, status, beforeCreatedAt, beforeJobId, limit);
    }

    private List<GenerationJobRecord> listInternal(String principalId, GenerationJobStatus status,
                                                   Instant beforeCreatedAt, String beforeJobId,
                                                   int limit) {
        if (limit < 1) throw new IllegalArgumentException("generation job list limit must be positive");
        if ((beforeCreatedAt == null) != (beforeJobId == null)) {
            throw new IllegalArgumentException("generation job cursor is incomplete");
        }
        String sql = "SELECT " + COLUMNS + " FROM omni_generation_job"
                + " WHERE tenant_id = ?"
                + (principalId == null ? "" : " AND principal_id = ?")
                + (status == null ? "" : " AND status = ?")
                + (beforeCreatedAt == null ? "" : " AND (created_at, job_id) < (?, ?::uuid)")
                + " ORDER BY created_at DESC, job_id DESC LIMIT ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 1;
            statement.setString(index++, tenantId);
            if (principalId != null) statement.setString(index++, principalId);
            if (status != null) statement.setString(index++, status.name());
            if (beforeCreatedAt != null) {
                statement.setTimestamp(index++, timestamp(beforeCreatedAt));
                statement.setString(index++, beforeJobId);
            }
            statement.setInt(index, limit);
            try (ResultSet result = statement.executeQuery()) {
                List<GenerationJobRecord> values = new ArrayList<>();
                while (result.next()) values.add(read(result));
                return values;
            }
        } catch (SQLException e) {
            throw databaseFailure("list generation jobs", e);
        }
    }

    @Override
    public Map<GenerationJobStatus, Long> countsByStatus() {
        Map<GenerationJobStatus, Long> values = new EnumMap<>(GenerationJobStatus.class);
        for (GenerationJobStatus status : GenerationJobStatus.values()) values.put(status, 0L);
        String sql = "SELECT status, count(*) AS count FROM omni_generation_job"
                + " WHERE tenant_id = ? GROUP BY status";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, tenantId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    values.put(GenerationJobStatus.valueOf(result.getString("status")),
                            result.getLong("count"));
                }
            }
            return values;
        } catch (SQLException e) {
            throw databaseFailure("count generation jobs", e);
        }
    }

    @Override
    public int purgeTerminalBefore(Instant cutoff, int limit) {
        if (cutoff == null || limit < 1 || limit > 10_000) {
            throw new IllegalArgumentException("generation purge boundary is invalid");
        }
        String sql = "DELETE FROM omni_generation_job WHERE job_id IN ("
                + "SELECT job_id FROM omni_generation_job WHERE tenant_id = ?"
                + " AND status IN ('SUCCEEDED','FAILED','CANCELLED') AND updated_at <= ?"
                + " ORDER BY updated_at LIMIT ?)";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, tenantId);
            statement.setTimestamp(2, timestamp(cutoff));
            statement.setInt(3, limit);
            return statement.executeUpdate();
        } catch (SQLException e) {
            throw databaseFailure("purge generation jobs", e);
        }
    }

    /**
     * 在单个数据库事务中提交 Worker 终态任务和 Webhook Outbox 事件。
     *
     * @param record 终态任务
     * @param workerId 当前 Worker 标识
     * @param event 可选 Webhook 事件
     * @param webhooks 共享同一数据源的 Webhook 仓储
     * @return 持久化后的任务快照
     */
    public GenerationJobRecord commitClaimedTerminal(GenerationJobRecord record, String workerId,
                                                      WebhookDeliveryRecord event,
                                                      PostgresWebhookDeliveryRepository webhooks) {
        return commitTerminal(record, workerId, true, event, webhooks);
    }

    /**
     * 在单个数据库事务中提交非 Worker 终态任务和 Webhook Outbox 事件。
     *
     * @param record 终态任务
     * @param event 可选 Webhook 事件
     * @param webhooks 共享同一数据源的 Webhook 仓储
     * @return 持久化后的任务快照
     */
    public GenerationJobRecord commitTerminal(GenerationJobRecord record, WebhookDeliveryRecord event,
                                               PostgresWebhookDeliveryRepository webhooks) {
        return commitTerminal(record, null, false, event, webhooks);
    }

    private GenerationJobRecord commitTerminal(GenerationJobRecord record, String workerId, boolean claimed,
                                                WebhookDeliveryRecord event,
                                                PostgresWebhookDeliveryRepository webhooks) {
        if (record == null || record.getStatus() == null || !record.getStatus().isTerminal()
                || webhooks == null || !webhooks.uses(dataSource)) {
            throw new IllegalArgumentException("transactional terminal commit dependencies are invalid");
        }
        try (Connection connection = dataSource.getConnection()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                GenerationJobRecord value = copy(record);
                if (event != null) {
                    WebhookDeliveryRecord stored = webhooks.enqueue(connection, event);
                    value.setTerminalEventId(stored.getEventId());
                    value.setTerminalEventQueuedAt(stored.getCreatedAt());
                }
                GenerationJobRecord saved = update(connection, value, workerId, claimed);
                connection.commit();
                return saved;
            } catch (RuntimeException | SQLException e) {
                try { connection.rollback(); } catch (SQLException rollbackFailure) { e.addSuppressed(rollbackFailure); }
                if (e instanceof RuntimeException) throw (RuntimeException) e;
                throw databaseFailure("commit terminal generation job", (SQLException) e);
            } finally {
                try { connection.setAutoCommit(originalAutoCommit); } catch (SQLException ignored) { }
            }
        } catch (SQLException e) {
            throw databaseFailure("commit terminal generation job", e);
        }
    }

    private GenerationJobRecord update(GenerationJobRecord record, String workerId, boolean claimed) {
        validate(record);
        if (!tenantId.equals(record.getTenantId())) throw new IllegalArgumentException("generation tenant mismatch");
        GenerationJobRecord value = copy(record);
        if (value.getStatus() != GenerationJobStatus.RUNNING) {
            value.setLeaseOwner(null);
            value.setLeaseUntil(null);
        }
        try (Connection connection = dataSource.getConnection()) {
            return update(connection, value, workerId, claimed);
        } catch (SQLException e) {
            throw databaseFailure("save generation job", e);
        }
    }

    private GenerationJobRecord update(Connection connection, GenerationJobRecord value,
                                       String workerId, boolean claimed) throws SQLException {
        validate(value);
        if (!tenantId.equals(value.getTenantId())) throw new IllegalArgumentException("generation tenant mismatch");
        if (value.getStatus() != GenerationJobStatus.RUNNING) {
            value.setLeaseOwner(null);
            value.setLeaseUntil(null);
        }
        String sql = "UPDATE omni_generation_job SET principal_id=?, correlation_id=?,"
                + " idempotency_key=?, request_sha256=?, mode=?, request_json=?::jsonb, status=?,"
                + " attempt_count=?, max_attempts=?, error_code=?, error_message=?,"
                + " current_stage=?, stage_started_at=?, deadline_at=?, draft_id=?::uuid, created_at=?, started_at=?, completed_at=?, updated_at=?, version=version+1, lease_owner=?,"
                + " lease_until=?, terminal_event_id=?, terminal_event_queued_at=?, artifacts_json=?::jsonb"
                + " WHERE tenant_id=? AND job_id=?::uuid AND version=?"
                + (claimed ? " AND status='RUNNING' AND lease_owner=? AND lease_until>?" : "");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = bindUpdate(statement, value);
            statement.setString(index++, tenantId);
            statement.setString(index++, value.getJobId());
            statement.setLong(index++, value.getVersion());
            if (claimed) {
                statement.setString(index++, workerId);
                statement.setTimestamp(index, timestamp(value.getUpdatedAt()));
            }
            if (statement.executeUpdate() != 1) {
                throw new GenerationJobConflictException(claimed
                        ? "generation job lease is not owned by this worker"
                        : "generation job was modified concurrently");
            }
            value.setVersion(value.getVersion() + 1L);
            return value;
        }
    }

    private Optional<GenerationJobRecord> selectOne(String sql, String... parameters) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < parameters.length; index++) {
                statement.setString(index + 1, parameters[index]);
            }
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(read(result)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw databaseFailure("read generation job", e);
        }
    }

    private int bindUpdate(PreparedStatement statement, GenerationJobRecord value) throws SQLException {
        int index = 1;
        statement.setString(index++, value.getPrincipalId());
        statement.setString(index++, value.getCorrelationId());
        nullableString(statement, index++, value.getIdempotencyKey());
        statement.setString(index++, value.getRequestSha256());
        statement.setString(index++, value.getMode().name());
        statement.setString(index++, json(value.getRequest()));
        statement.setString(index++, value.getStatus().name());
        statement.setInt(index++, value.getAttemptCount());
        statement.setInt(index++, value.getMaxAttempts());
        nullableString(statement, index++, value.getErrorCode());
        nullableString(statement, index++, value.getErrorMessage());
        statement.setString(index++, value.getCurrentStage().name());
        nullableTimestamp(statement, index++, value.getStageStartedAt());
        nullableTimestamp(statement, index++, value.getDeadlineAt());
        nullableString(statement, index++, value.getDraftId());
        statement.setTimestamp(index++, timestamp(value.getCreatedAt()));
        nullableTimestamp(statement, index++, value.getStartedAt());
        nullableTimestamp(statement, index++, value.getCompletedAt());
        statement.setTimestamp(index++, timestamp(value.getUpdatedAt()));
        nullableString(statement, index++, value.getLeaseOwner());
        nullableTimestamp(statement, index++, value.getLeaseUntil());
        nullableString(statement, index++, value.getTerminalEventId());
        nullableTimestamp(statement, index++, value.getTerminalEventQueuedAt());
        statement.setString(index++, json(value.getArtifacts()));
        return index;
    }

    private void bindRecord(PreparedStatement statement, GenerationJobRecord value, int start) throws SQLException {
        int index = start;
        statement.setString(index++, value.getJobId());
        statement.setString(index++, value.getTenantId());
        statement.setString(index++, value.getPrincipalId());
        statement.setString(index++, value.getCorrelationId());
        nullableString(statement, index++, value.getIdempotencyKey());
        statement.setString(index++, value.getRequestSha256());
        statement.setString(index++, value.getMode().name());
        statement.setString(index++, json(value.getRequest()));
        statement.setString(index++, value.getStatus().name());
        statement.setInt(index++, value.getAttemptCount());
        statement.setInt(index++, value.getMaxAttempts());
        nullableString(statement, index++, value.getErrorCode());
        nullableString(statement, index++, value.getErrorMessage());
        statement.setString(index++, value.getCurrentStage().name());
        nullableTimestamp(statement, index++, value.getStageStartedAt());
        nullableTimestamp(statement, index++, value.getDeadlineAt());
        nullableString(statement, index++, value.getDraftId());
        statement.setTimestamp(index++, timestamp(value.getCreatedAt()));
        nullableTimestamp(statement, index++, value.getStartedAt());
        nullableTimestamp(statement, index++, value.getCompletedAt());
        statement.setTimestamp(index++, timestamp(value.getUpdatedAt()));
        statement.setLong(index++, value.getVersion());
        nullableString(statement, index++, value.getLeaseOwner());
        nullableTimestamp(statement, index++, value.getLeaseUntil());
        nullableString(statement, index++, value.getTerminalEventId());
        nullableTimestamp(statement, index++, value.getTerminalEventQueuedAt());
        statement.setString(index, json(value.getArtifacts()));
    }

    private GenerationJobRecord read(ResultSet result) throws SQLException {
        GenerationJobRecord value = new GenerationJobRecord();
        value.setJobId(result.getString("job_id"));
        value.setTenantId(result.getString("tenant_id"));
        value.setPrincipalId(result.getString("principal_id"));
        value.setCorrelationId(result.getString("correlation_id"));
        value.setIdempotencyKey(result.getString("idempotency_key"));
        value.setRequestSha256(result.getString("request_sha256"));
        value.setMode(GenerationMode.valueOf(result.getString("mode")));
        try {
            value.setRequest(mapper.readTree(result.getString("request_json")));
            value.setArtifacts(mapper.readValue(result.getString("artifacts_json"), artifactsType));
        } catch (JsonProcessingException e) {
            throw new SQLException("generation job JSON is invalid", e);
        }
        value.setStatus(GenerationJobStatus.valueOf(result.getString("status")));
        value.setAttemptCount(result.getInt("attempt_count"));
        value.setMaxAttempts(result.getInt("max_attempts"));
        value.setErrorCode(result.getString("error_code"));
        value.setErrorMessage(result.getString("error_message"));
        value.setCurrentStage(GenerationStage.valueOf(result.getString("current_stage")));
        value.setStageStartedAt(instant(result, "stage_started_at"));
        value.setDeadlineAt(instant(result, "deadline_at"));
        value.setDraftId(result.getString("draft_id"));
        value.setCreatedAt(instant(result, "created_at"));
        value.setStartedAt(instant(result, "started_at"));
        value.setCompletedAt(instant(result, "completed_at"));
        value.setUpdatedAt(instant(result, "updated_at"));
        value.setVersion(result.getLong("version"));
        value.setLeaseOwner(result.getString("lease_owner"));
        value.setLeaseUntil(instant(result, "lease_until"));
        value.setTerminalEventId(result.getString("terminal_event_id"));
        value.setTerminalEventQueuedAt(instant(result, "terminal_event_queued_at"));
        return value;
    }

    private void validate(GenerationJobRecord value) {
        if (value == null || value.getJobId() == null || value.getTenantId() == null
                || value.getPrincipalId() == null || value.getCorrelationId() == null
                || value.getRequestSha256() == null || value.getMode() == null || value.getRequest() == null
                || value.getStatus() == null || value.getCurrentStage() == null
                || value.getCreatedAt() == null || value.getUpdatedAt() == null) {
            throw new IllegalArgumentException("generation job record is incomplete");
        }
    }

    private void validateLease(String workerId, Instant now, Instant leaseUntil) {
        if (workerId == null || !workerId.matches("[A-Za-z0-9._:-]{1,128}")
                || now == null || leaseUntil == null || !leaseUntil.isAfter(now)) {
            throw new IllegalArgumentException("generation job lease is invalid");
        }
    }

    private String json(Object value) throws SQLException {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new SQLException("failed to encode generation job JSON", e);
        }
    }

    private GenerationJobRecord copy(GenerationJobRecord value) {
        return mapper.convertValue(mapper.valueToTree(value), GenerationJobRecord.class);
    }

    private static Timestamp timestamp(Instant value) { return Timestamp.from(value); }

    private static Instant instant(ResultSet result, String column) throws SQLException {
        Timestamp value = result.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static void nullableString(PreparedStatement statement, int index, String value) throws SQLException {
        if (value == null) statement.setNull(index, Types.VARCHAR);
        else statement.setString(index, value);
    }

    private static void nullableTimestamp(PreparedStatement statement, int index, Instant value)
            throws SQLException {
        if (value == null) statement.setNull(index, Types.TIMESTAMP_WITH_TIMEZONE);
        else statement.setTimestamp(index, timestamp(value));
    }

    private static IllegalStateException databaseFailure(String operation, SQLException cause) {
        return new IllegalStateException("failed to " + operation, cause);
    }
}
