package cn.bugstack.application.generation;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import cn.bugstack.application.generation.webhook.PostgresWebhookDeliveryRepository;
import cn.bugstack.application.generation.webhook.WebhookDeliveryRepository;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** 管理 PostgreSQL 连接池，并为所有租户共享由人工维护表结构的同一数据库。 */
public final class PostgresGenerationJobRepositoryProvider implements GenerationJobRepositoryProvider {

    private final HikariDataSource dataSource;

    /**
     * 创建 PostgreSQL 仓储提供器。
     *
     * <p>该提供器不会创建或变更数据库对象，连接前应由运维人员准备所需表结构。</p>
     *
     * @param jdbcUrl PostgreSQL JDBC URL
     * @param username 数据库用户名
     * @param password 数据库密码
     * @param maximumPoolSize 最大连接池大小，范围为 2～100
     */
    public PostgresGenerationJobRepositoryProvider(String jdbcUrl, String username, String password,
                                                    int maximumPoolSize) {
        if (jdbcUrl == null || !jdbcUrl.startsWith("jdbc:postgresql://")
                || username == null || username.isBlank() || password == null
                || maximumPoolSize < 2 || maximumPoolSize > 100) {
            throw new IllegalArgumentException("PostgreSQL generation configuration is invalid");
        }
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(maximumPoolSize);
        config.setMinimumIdle(Math.min(2, maximumPoolSize));
        config.setConnectionTimeout(10_000L);
        config.setValidationTimeout(3_000L);
        config.setKeepaliveTime(60_000L);
        config.setPoolName("omni-office-generation");
        config.addDataSourceProperty("tcpKeepAlive", "true");
        this.dataSource = new HikariDataSource(config);
    }

    @Override
    public GenerationJobRepository repository(String tenantId) {
        return new PostgresGenerationJobRepository(dataSource, tenantId);
    }

    @Override
    public boolean isReady() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(2);
        } catch (SQLException | RuntimeException e) {
            return false;
        }
    }

    @Override
    public WebhookDeliveryRepository webhookRepository(Path fileFallbackRoot) {
        return new PostgresWebhookDeliveryRepository(dataSource);
    }

    @Override
    public Set<String> recoverableTenantIds(Instant now) {
        if (now == null) throw new IllegalArgumentException("generation recovery time is required");
        String sql = "SELECT DISTINCT tenant_id FROM omni_generation_job"
                + " WHERE status IN ('QUEUED','RUNNING')"
                + " OR (terminal_event_id IS NULL AND request_json ? 'webhookId')";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {
            Set<String> values = new LinkedHashSet<>();
            while (result.next()) values.add(result.getString(1));
            return values;
        } catch (SQLException e) {
            throw new IllegalStateException("failed to discover recoverable generation tenants", e);
        }
    }

    @Override
    public Map<GenerationJobStatus, Long> countsByStatus() {
        Map<GenerationJobStatus, Long> values = new EnumMap<>(GenerationJobStatus.class);
        for (GenerationJobStatus status : GenerationJobStatus.values()) values.put(status, 0L);
        String sql = "SELECT status, count(*) AS count FROM omni_generation_job GROUP BY status";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                values.put(GenerationJobStatus.valueOf(result.getString("status")), result.getLong("count"));
            }
            return values;
        } catch (SQLException e) {
            throw new IllegalStateException("failed to count generation jobs", e);
        }
    }

    @Override
    public int purgeTerminalBefore(Instant cutoff, int limit) {
        if (cutoff == null || limit < 1 || limit > 10_000) {
            throw new IllegalArgumentException("generation purge boundary is invalid");
        }
        String sql = "DELETE FROM omni_generation_job WHERE job_id IN ("
                + "SELECT job_id FROM omni_generation_job"
                + " WHERE status IN ('SUCCEEDED','FAILED','CANCELLED') AND updated_at <= ?"
                + " ORDER BY updated_at LIMIT ?)";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setTimestamp(1, java.sql.Timestamp.from(cutoff));
            statement.setInt(2, limit);
            return statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("failed to purge generation jobs", e);
        }
    }

    @Override
    public void close() {
        dataSource.close();
    }
}
