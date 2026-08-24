package cn.bugstack.application.generation.webhook;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** PostgreSQL Webhook Outbox，支持跨实例原子领取和租约过期恢复。 */
public final class PostgresWebhookDeliveryRepository implements WebhookDeliveryRepository {

    private static final String COLUMNS = "event_id, event_type, tenant_id, webhook_id, job_id,"
            + " payload_json, status, attempt_count, max_attempts, response_status, last_error,"
            + " next_attempt_at, created_at, updated_at, delivered_at, version, lease_owner, lease_until";
    private final DataSource dataSource;
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    /**
     * 创建 PostgreSQL Webhook Outbox 仓储。
     *
     * @param dataSource 共享数据源
     */
    public PostgresWebhookDeliveryRepository(DataSource dataSource) {
        if (dataSource == null) throw new IllegalArgumentException("webhook data source is required");
        this.dataSource = dataSource;
    }

    @Override
    public WebhookDeliveryRecord enqueue(WebhookDeliveryRecord record) {
        try (Connection connection = dataSource.getConnection()) {
            return enqueue(connection, record);
        } catch (SQLException e) {
            throw failure("enqueue webhook delivery", e);
        }
    }

    /**
     * 使用调用方事务连接幂等入队。
     *
     * @param connection 已开启事务的数据库连接
     * @param record 待入队事件
     * @return 持久化后的事件快照
     * @throws SQLException 数据库操作失败时抛出
     */
    public WebhookDeliveryRecord enqueue(Connection connection, WebhookDeliveryRecord record)
            throws SQLException {
        validate(record);
        WebhookDeliveryRecord value = copy(record);
        value.setVersion(1L);
        String sql = "INSERT INTO omni_webhook_delivery (" + COLUMNS + ") VALUES ("
                + "?::uuid,?,?,?,?::uuid,?::jsonb,?,?,?,?,?,?,?,?,?,?,?,?)"
                + " ON CONFLICT (tenant_id, job_id, event_type) DO NOTHING";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindRecord(statement, value);
            if (statement.executeUpdate() == 1) return value;
            return findByEventKey(connection, value.getTenantId(), value.getJobId(), value.getEventType())
                    .orElseThrow(() -> new IllegalStateException("conflicting webhook event disappeared"));
        }
    }

    /**
     * 判断仓储是否使用指定数据源，以保证跨仓储事务安全。
     *
     * @param candidate 候选数据源
     * @return 引用同一数据源时返回 {@code true}
     */
    public boolean uses(DataSource candidate) {
        return dataSource == candidate;
    }

    @Override
    public WebhookDeliveryRecord save(WebhookDeliveryRecord record) {
        return update(record, null, null, false);
    }

    @Override
    public Optional<WebhookDeliveryRecord> findByEventKey(String tenantId, String jobId, String eventType) {
        try (Connection connection = dataSource.getConnection()) {
            return findByEventKey(connection, tenantId, jobId, eventType);
        } catch (SQLException e) {
            throw failure("find webhook delivery", e);
        }
    }

    private Optional<WebhookDeliveryRecord> findByEventKey(Connection connection, String tenantId,
                                                            String jobId, String eventType)
            throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM omni_webhook_delivery"
                + " WHERE tenant_id=? AND job_id=?::uuid AND event_type=?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, tenantId);
            statement.setString(2, jobId);
            statement.setString(3, eventType);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(read(result)) : Optional.empty();
            }
        }
    }

    @Override
    public List<WebhookDeliveryRecord> claimDue(String workerId, Instant now,
                                                Instant leaseUntil, int limit) {
        validateLease(workerId, now, leaseUntil);
        if (limit < 1 || limit > 1000) throw new IllegalArgumentException("webhook claim limit is invalid");
        String sql = "WITH candidate AS (SELECT event_id FROM omni_webhook_delivery"
                + " WHERE status IN ('PENDING','RETRYING') AND next_attempt_at<=?"
                + " AND (lease_until IS NULL OR lease_until<=?)"
                + " ORDER BY next_attempt_at, created_at LIMIT ? FOR UPDATE SKIP LOCKED)"
                + " UPDATE omni_webhook_delivery item SET lease_owner=?, lease_until=?,"
                + " updated_at=?, version=item.version+1 FROM candidate"
                + " WHERE item.event_id=candidate.event_id RETURNING item.*";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setTimestamp(1, timestamp(now));
            statement.setTimestamp(2, timestamp(now));
            statement.setInt(3, limit);
            statement.setString(4, workerId);
            statement.setTimestamp(5, timestamp(leaseUntil));
            statement.setTimestamp(6, timestamp(now));
            try (ResultSet result = statement.executeQuery()) {
                List<WebhookDeliveryRecord> values = new ArrayList<>();
                while (result.next()) values.add(read(result));
                return values;
            }
        } catch (SQLException e) {
            throw failure("claim webhook deliveries", e);
        }
    }

    @Override
    public WebhookDeliveryRecord saveClaimed(WebhookDeliveryRecord record, String workerId, Instant now) {
        validateLeaseOwner(workerId, now);
        return update(record, workerId, now, true);
    }

    @Override
    public List<WebhookDeliveryRecord> list(String tenantId, int limit) {
        if (tenantId == null || limit < 1) throw new IllegalArgumentException("webhook list query is invalid");
        String sql = "SELECT " + COLUMNS + " FROM omni_webhook_delivery"
                + " WHERE tenant_id=? ORDER BY created_at DESC LIMIT ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, tenantId);
            statement.setInt(2, limit);
            try (ResultSet result = statement.executeQuery()) {
                List<WebhookDeliveryRecord> values = new ArrayList<>();
                while (result.next()) values.add(read(result));
                return values;
            }
        } catch (SQLException e) {
            throw failure("list webhook deliveries", e);
        }
    }

    @Override
    public Map<WebhookDeliveryStatus, Long> countsByStatus() {
        Map<WebhookDeliveryStatus, Long> values = new EnumMap<>(WebhookDeliveryStatus.class);
        for (WebhookDeliveryStatus status : WebhookDeliveryStatus.values()) values.put(status, 0L);
        String sql = "SELECT status, COUNT(*) FROM omni_webhook_delivery GROUP BY status";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                values.put(WebhookDeliveryStatus.valueOf(result.getString(1)), result.getLong(2));
            }
            return values;
        } catch (SQLException e) {
            throw failure("count webhook deliveries", e);
        }
    }

    @Override
    public Map<WebhookDeliveryStatus, Long> countsByStatus(String tenantId) {
        if (tenantId == null || !tenantId.matches("[A-Za-z0-9._-]{1,64}")) {
            throw new IllegalArgumentException("webhook tenant id is invalid");
        }
        Map<WebhookDeliveryStatus, Long> values = new EnumMap<>(WebhookDeliveryStatus.class);
        for (WebhookDeliveryStatus status : WebhookDeliveryStatus.values()) values.put(status, 0L);
        String sql = "SELECT status, count(*) AS count FROM omni_webhook_delivery"
                + " WHERE tenant_id = ? GROUP BY status";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, tenantId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    values.put(WebhookDeliveryStatus.valueOf(result.getString("status")),
                            result.getLong("count"));
                }
            }
            return values;
        } catch (SQLException e) {
            throw failure("count tenant webhook deliveries", e);
        }
    }

    @Override
    public WebhookDeliveryRecord redrive(String tenantId, String eventId, Instant now,
                                         int additionalAttempts) {
        if (tenantId == null || !tenantId.matches("[A-Za-z0-9._-]{1,64}") || eventId == null
                || now == null || additionalAttempts < 1 || additionalAttempts > 20) {
            throw new IllegalArgumentException("webhook redrive request is invalid");
        }
        String sql = "UPDATE omni_webhook_delivery SET status='RETRYING',"
                + " max_attempts=LEAST(20, attempt_count + ?), response_status=NULL, last_error=NULL,"
                + " next_attempt_at=?, delivered_at=NULL, lease_owner=NULL, lease_until=NULL,"
                + " updated_at=?, version=version+1"
                + " WHERE tenant_id=? AND event_id=?::uuid AND status='DEAD'"
                + " AND attempt_count<20 RETURNING *";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, additionalAttempts);
            statement.setTimestamp(2, timestamp(now));
            statement.setTimestamp(3, timestamp(now));
            statement.setString(4, tenantId);
            statement.setString(5, eventId);
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) return read(result);
                throw new IllegalStateException("webhook event is not DEAD or does not exist");
            }
        } catch (SQLException e) {
            throw failure("redrive webhook delivery", e);
        }
    }

    @Override
    public int purgeTerminalBefore(Instant cutoff, int limit) {
        if (cutoff == null || limit < 1 || limit > 10_000) {
            throw new IllegalArgumentException("webhook purge boundary is invalid");
        }
        String sql = "DELETE FROM omni_webhook_delivery WHERE event_id IN ("
                + "SELECT event_id FROM omni_webhook_delivery WHERE status IN ('DELIVERED','DEAD')"
                + " AND updated_at <= ? ORDER BY updated_at LIMIT ?)";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setTimestamp(1, timestamp(cutoff));
            statement.setInt(2, limit);
            return statement.executeUpdate();
        } catch (SQLException e) {
            throw failure("purge webhook deliveries", e);
        }
    }

    private WebhookDeliveryRecord update(WebhookDeliveryRecord record, String workerId,
                                         Instant now, boolean claimed) {
        validate(record);
        WebhookDeliveryRecord value = copy(record);
        value.setLeaseOwner(null);
        value.setLeaseUntil(null);
        String sql = "UPDATE omni_webhook_delivery SET event_type=?, tenant_id=?, webhook_id=?,"
                + " job_id=?::uuid, payload_json=?::jsonb, status=?, attempt_count=?, max_attempts=?,"
                + " response_status=?, last_error=?, next_attempt_at=?, created_at=?, updated_at=?,"
                + " delivered_at=?, version=version+1, lease_owner=?, lease_until=?"
                + " WHERE event_id=?::uuid AND version=?"
                + (claimed ? " AND lease_owner=? AND lease_until>?" : "");
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = bindUpdate(statement, value);
            statement.setString(index++, value.getEventId());
            statement.setLong(index++, value.getVersion());
            if (claimed) {
                statement.setString(index++, workerId);
                statement.setTimestamp(index, timestamp(now));
            }
            if (statement.executeUpdate() != 1) {
                throw new IllegalStateException(claimed
                        ? "webhook delivery lease is not owned by this worker"
                        : "webhook delivery was modified concurrently");
            }
            value.setVersion(value.getVersion() + 1L);
            return value;
        } catch (SQLException e) {
            throw failure("save webhook delivery", e);
        }
    }

    private int bindUpdate(PreparedStatement statement, WebhookDeliveryRecord value) throws SQLException {
        int index = 1;
        statement.setString(index++, value.getEventType());
        statement.setString(index++, value.getTenantId());
        statement.setString(index++, value.getWebhookId());
        statement.setString(index++, value.getJobId());
        statement.setString(index++, json(value.getPayload()));
        statement.setString(index++, value.getStatus().name());
        statement.setInt(index++, value.getAttemptCount());
        statement.setInt(index++, value.getMaxAttempts());
        nullableInteger(statement, index++, value.getResponseStatus());
        nullableString(statement, index++, value.getLastError());
        nullableTimestamp(statement, index++, value.getNextAttemptAt());
        statement.setTimestamp(index++, timestamp(value.getCreatedAt()));
        statement.setTimestamp(index++, timestamp(value.getUpdatedAt()));
        nullableTimestamp(statement, index++, value.getDeliveredAt());
        nullableString(statement, index++, value.getLeaseOwner());
        nullableTimestamp(statement, index++, value.getLeaseUntil());
        return index;
    }

    private void bindRecord(PreparedStatement statement, WebhookDeliveryRecord value) throws SQLException {
        int index = 1;
        statement.setString(index++, value.getEventId());
        statement.setString(index++, value.getEventType());
        statement.setString(index++, value.getTenantId());
        statement.setString(index++, value.getWebhookId());
        statement.setString(index++, value.getJobId());
        statement.setString(index++, json(value.getPayload()));
        statement.setString(index++, value.getStatus().name());
        statement.setInt(index++, value.getAttemptCount());
        statement.setInt(index++, value.getMaxAttempts());
        nullableInteger(statement, index++, value.getResponseStatus());
        nullableString(statement, index++, value.getLastError());
        nullableTimestamp(statement, index++, value.getNextAttemptAt());
        statement.setTimestamp(index++, timestamp(value.getCreatedAt()));
        statement.setTimestamp(index++, timestamp(value.getUpdatedAt()));
        nullableTimestamp(statement, index++, value.getDeliveredAt());
        statement.setLong(index++, value.getVersion());
        nullableString(statement, index++, value.getLeaseOwner());
        nullableTimestamp(statement, index, value.getLeaseUntil());
    }

    private WebhookDeliveryRecord read(ResultSet result) throws SQLException {
        try {
            WebhookDeliveryRecord value = new WebhookDeliveryRecord();
            value.setEventId(result.getString("event_id"));
            value.setEventType(result.getString("event_type"));
            value.setTenantId(result.getString("tenant_id"));
            value.setWebhookId(result.getString("webhook_id"));
            value.setJobId(result.getString("job_id"));
            value.setPayload(mapper.readTree(result.getString("payload_json")));
            value.setStatus(WebhookDeliveryStatus.valueOf(result.getString("status")));
            value.setAttemptCount(result.getInt("attempt_count"));
            value.setMaxAttempts(result.getInt("max_attempts"));
            value.setResponseStatus((Integer) result.getObject("response_status"));
            value.setLastError(result.getString("last_error"));
            value.setNextAttemptAt(instant(result, "next_attempt_at"));
            value.setCreatedAt(instant(result, "created_at"));
            value.setUpdatedAt(instant(result, "updated_at"));
            value.setDeliveredAt(instant(result, "delivered_at"));
            value.setVersion(result.getLong("version"));
            value.setLeaseOwner(result.getString("lease_owner"));
            value.setLeaseUntil(instant(result, "lease_until"));
            return value;
        } catch (JsonProcessingException e) {
            throw new SQLException("webhook payload JSON is invalid", e);
        }
    }

    private void validate(WebhookDeliveryRecord value) {
        if (value == null || value.getEventId() == null || value.getEventType() == null
                || value.getTenantId() == null || value.getWebhookId() == null || value.getJobId() == null
                || value.getPayload() == null || value.getStatus() == null || value.getCreatedAt() == null
                || value.getUpdatedAt() == null || value.getMaxAttempts() < 1) {
            throw new IllegalArgumentException("webhook delivery record is incomplete");
        }
    }

    private void validateLease(String workerId, Instant now, Instant leaseUntil) {
        validateLeaseOwner(workerId, now);
        if (leaseUntil == null || !leaseUntil.isAfter(now)) {
            throw new IllegalArgumentException("webhook delivery lease is invalid");
        }
    }

    private void validateLeaseOwner(String workerId, Instant now) {
        if (workerId == null || !workerId.matches("[A-Za-z0-9._:-]{1,128}") || now == null) {
            throw new IllegalArgumentException("webhook delivery lease is invalid");
        }
    }

    private String json(Object value) throws SQLException {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new SQLException("failed to encode webhook payload", e);
        }
    }

    private WebhookDeliveryRecord copy(WebhookDeliveryRecord value) {
        return mapper.convertValue(mapper.valueToTree(value), WebhookDeliveryRecord.class);
    }

    private static Timestamp timestamp(Instant value) { return Timestamp.from(value); }
    private static Instant instant(ResultSet result, String name) throws SQLException {
        Timestamp value = result.getTimestamp(name);
        return value == null ? null : value.toInstant();
    }
    private static void nullableString(PreparedStatement statement, int index, String value) throws SQLException {
        if (value == null) statement.setNull(index, Types.VARCHAR); else statement.setString(index, value);
    }
    private static void nullableInteger(PreparedStatement statement, int index, Integer value) throws SQLException {
        if (value == null) statement.setNull(index, Types.INTEGER); else statement.setInt(index, value);
    }
    private static void nullableTimestamp(PreparedStatement statement, int index, Instant value) throws SQLException {
        if (value == null) statement.setNull(index, Types.TIMESTAMP_WITH_TIMEZONE);
        else statement.setTimestamp(index, timestamp(value));
    }
    private static IllegalStateException failure(String operation, SQLException cause) {
        return new IllegalStateException("failed to " + operation, cause);
    }
}
