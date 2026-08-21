package cn.bugstack.application.generation;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import cn.bugstack.application.generation.webhook.PostgresWebhookDeliveryRepository;
import cn.bugstack.application.generation.webhook.WebhookDeliveryRepository;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;

/** 管理 PostgreSQL 连接池和 Flyway 迁移，并为所有租户共享同一数据库。 */
public final class PostgresGenerationJobRepositoryProvider implements GenerationJobRepositoryProvider {

    private final HikariDataSource dataSource;

    /**
     * 创建 PostgreSQL 仓储提供器并执行 Flyway 迁移。
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
        try {
            Flyway.configure().dataSource(dataSource)
                    .locations("classpath:db/migration")
                    .validateMigrationNaming(true)
                    .load()
                    .migrate();
        } catch (RuntimeException migrationFailure) {
            dataSource.close();
            throw migrationFailure;
        }
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
    public void close() {
        dataSource.close();
    }
}
