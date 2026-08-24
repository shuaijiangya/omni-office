package cn.bugstack.application.generation;

import cn.bugstack.application.generation.webhook.PostgresWebhookDeliveryRepository;
import cn.bugstack.application.generation.webhook.WebhookDeliveryRecord;
import cn.bugstack.application.generation.webhook.WebhookDeliveryStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import javax.sql.DataSource;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PostgresGenerationPersistenceTest {

    private EmbeddedPostgres postgres;
    private DataSource dataSource;

    @BeforeAll
    void startPostgres() throws Exception {
        postgres = EmbeddedPostgres.builder().start();
        dataSource = postgres.getPostgresDatabase();
        initializeTestSchema();
    }

    @BeforeEach
    void clearData() throws Exception {
        try (java.sql.Connection connection = dataSource.getConnection();
             java.sql.Statement statement = connection.createStatement()) {
            statement.execute("TRUNCATE omni_webhook_delivery, omni_generation_job");
        }
    }

    @AfterAll
    void stopPostgres() throws Exception {
        if (postgres != null) postgres.close();
    }

    @Test
    void claimsByLeaseAndCommitsTerminalOutboxAtomically() throws Exception {
        PostgresGenerationJobRepository first = new PostgresGenerationJobRepository(dataSource, "tenant-a");
        PostgresGenerationJobRepository second = new PostgresGenerationJobRepository(dataSource, "tenant-a");
        PostgresGenerationJobRepository otherTenant = new PostgresGenerationJobRepository(
                dataSource, "tenant-b");
        PostgresWebhookDeliveryRepository webhooks = new PostgresWebhookDeliveryRepository(dataSource);
        Instant now = Instant.parse("2026-08-21T00:00:00Z");

        GenerationJobRecord created = first.create(job(now));
        GenerationJobRecord duplicate = job(now);
        duplicate.setJobId(UUID.randomUUID().toString());
        assertThrows(GenerationJobConflictException.class, () -> first.create(duplicate));
        assertFalse(otherTenant.find(created.getJobId()).isPresent());

        GenerationJobRecord claimed = first.claimNext(
                "worker-a", now, now.plusSeconds(60)).orElseThrow();
        assertFalse(second.claimNext("worker-b", now.plusSeconds(1),
                now.plusSeconds(61)).isPresent());
        claimed.setStatus(GenerationJobStatus.SUCCEEDED);
        claimed.setCompletedAt(now.plusSeconds(2));
        claimed.setUpdatedAt(now.plusSeconds(2));
        WebhookDeliveryRecord event = event(claimed, now.plusSeconds(2));

        GenerationJobRecord completed = first.commitClaimedTerminal(
                claimed, "worker-a", event, webhooks);
        assertEquals(GenerationJobStatus.SUCCEEDED, completed.getStatus());
        assertEquals(event.getEventId(), completed.getTerminalEventId());
        assertNotNull(webhooks.findByEventKey("tenant-a", completed.getJobId(),
                "generation.succeeded").orElseThrow());
    }

    @Test
    void rollsBackTerminalJobWhenOutboxInsertIsInvalid() throws Exception {
        PostgresGenerationJobRepository jobs = new PostgresGenerationJobRepository(dataSource, "tenant-a");
        PostgresWebhookDeliveryRepository webhooks = new PostgresWebhookDeliveryRepository(dataSource);
        Instant now = Instant.parse("2026-08-21T00:00:00Z");
        jobs.create(job(now));
        GenerationJobRecord claimed = jobs.claimNext(
                "worker-a", now, now.plusSeconds(60)).orElseThrow();
        claimed.setStatus(GenerationJobStatus.SUCCEEDED);
        claimed.setCompletedAt(now.plusSeconds(2));
        claimed.setUpdatedAt(now.plusSeconds(2));
        WebhookDeliveryRecord invalid = event(claimed, now.plusSeconds(2));
        invalid.setPayload(null);
        GenerationJobRecord terminal = claimed;

        assertThrows(IllegalArgumentException.class,
                () -> jobs.commitClaimedTerminal(terminal, "worker-a", invalid, webhooks));
        GenerationJobRecord persisted = jobs.find(claimed.getJobId()).orElseThrow();
        assertEquals(GenerationJobStatus.RUNNING, persisted.getStatus());
        assertEquals("worker-a", persisted.getLeaseOwner());
    }

    @Test
    void productionProviderUsesExistingSchemaAndExposesReadiness() {
        try (PostgresGenerationJobRepositoryProvider provider =
                     new PostgresGenerationJobRepositoryProvider(
                             postgres.getJdbcUrl("postgres", "postgres"), "postgres", "postgres", 2)) {
            assertTrue(provider.isReady());
            GenerationJobRepository repository = provider.repository("tenant-a");
            GenerationJobRecord created = repository.create(job(Instant.now()));
            assertEquals(created.getJobId(), repository.find(created.getJobId()).orElseThrow().getJobId());
            assertNotNull(provider.webhookRepository(java.nio.file.Path.of("unused")));
        }
    }

    @Test
    void twoWorkersAtomicallyClaimEachJobOnlyOnce() throws Exception {
        PostgresGenerationJobRepository first = new PostgresGenerationJobRepository(dataSource, "tenant-a");
        PostgresGenerationJobRepository second = new PostgresGenerationJobRepository(dataSource, "tenant-a");
        Instant now = Instant.parse("2026-08-21T00:00:00Z");
        for (int index = 0; index < 20; index++) {
            first.create(job(now.plusMillis(index), "request-" + index));
        }
        Set<String> claimedIds = ConcurrentHashMap.newKeySet();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> left = executor.submit(() -> claimAll(first, "worker-a", now, start, claimedIds));
            Future<Integer> right = executor.submit(() -> claimAll(second, "worker-b", now, start, claimedIds));
            start.countDown();
            assertEquals(20, left.get() + right.get());
            assertEquals(20, claimedIds.size());
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void concurrentAdmissionsCannotExceedTenantActiveQuota() throws Exception {
        PostgresGenerationJobRepository first = new PostgresGenerationJobRepository(dataSource, "tenant-a");
        PostgresGenerationJobRepository second = new PostgresGenerationJobRepository(dataSource, "tenant-a");
        Instant now = Instant.parse("2026-08-21T12:00:00Z");
        GenerationQuota quota = new GenerationQuota(1, 10);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> left = executor.submit(() -> admit(first, job(now, "quota-a"), quota, start));
            Future<Boolean> right = executor.submit(() -> admit(second, job(now, "quota-b"), quota, start));
            start.countDown();
            int successes = (left.get() ? 1 : 0) + (right.get() ? 1 : 0);
            assertEquals(1, successes);
            assertEquals(1, first.list(10).size());
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    private boolean admit(PostgresGenerationJobRepository repository, GenerationJobRecord job,
                          GenerationQuota quota, CountDownLatch start) throws Exception {
        start.await();
        try {
            repository.create(job, quota, Instant.parse("2026-08-21T00:00:00Z"));
            return true;
        } catch (GenerationQuotaExceededException expected) {
            return false;
        }
    }

    /** 初始化仅供 PostgreSQL 集成测试使用的最终态表结构。 */
    private void initializeTestSchema() throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/postgres/generation-schema.sql")) {
            if (input == null) throw new IllegalStateException("missing PostgreSQL test schema");
            String script = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            try (java.sql.Connection connection = dataSource.getConnection();
                 java.sql.Statement statement = connection.createStatement()) {
                for (String sql : script.split(";")) {
                    if (!sql.isBlank()) statement.execute(sql);
                }
            }
        }
    }

    private int claimAll(PostgresGenerationJobRepository repository, String workerId, Instant now,
                         CountDownLatch start, Set<String> claimedIds) throws Exception {
        start.await();
        int count = 0;
        while (true) {
            java.util.Optional<GenerationJobRecord> claimed = repository.claimNext(
                    workerId, now, now.plusSeconds(60));
            if (claimed.isEmpty()) return count;
            if (!claimedIds.add(claimed.get().getJobId())) {
                throw new AssertionError("generation job was claimed twice");
            }
            count++;
        }
    }

    private GenerationJobRecord job(Instant now) {
        return job(now, "request-1");
    }

    private GenerationJobRecord job(Instant now, String idempotencyKey) {
        GenerationJobRecord value = new GenerationJobRecord();
        value.setJobId(UUID.randomUUID().toString());
        value.setTenantId("tenant-a");
        value.setPrincipalId("alice");
        value.setCorrelationId("trace-1");
        value.setIdempotencyKey(idempotencyKey);
        value.setRequestSha256("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        value.setMode(GenerationMode.DOCUMENT_SPEC);
        value.setRequest(new ObjectMapper().createObjectNode().put("mode", "DOCUMENT_SPEC"));
        value.setStatus(GenerationJobStatus.QUEUED);
        value.setMaxAttempts(2);
        value.setCreatedAt(now);
        value.setUpdatedAt(now);
        return value;
    }

    private WebhookDeliveryRecord event(GenerationJobRecord job, Instant now) {
        WebhookDeliveryRecord value = new WebhookDeliveryRecord();
        value.setEventId(UUID.randomUUID().toString());
        value.setEventType("generation.succeeded");
        value.setTenantId(job.getTenantId());
        value.setWebhookId("erp");
        value.setJobId(job.getJobId());
        value.setPayload(new ObjectMapper().createObjectNode().put("eventId", value.getEventId()));
        value.setStatus(WebhookDeliveryStatus.PENDING);
        value.setNextAttemptAt(now);
        value.setCreatedAt(now);
        value.setUpdatedAt(now);
        return value;
    }
}
