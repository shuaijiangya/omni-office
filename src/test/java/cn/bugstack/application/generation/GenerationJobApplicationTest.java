package cn.bugstack.application.generation;

import cn.bugstack.application.external.ExternalDocumentToolApplication;
import cn.bugstack.application.ai.InternalAiDocumentService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GenerationJobApplicationTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void persistsExecutesAndDeduplicatesDocumentJobs() throws Exception {
        Path root = Files.createTempDirectory("generation-jobs");
        FileGenerationJobRepository repository = new FileGenerationJobRepository(root.resolve("jobs"));
        ExternalDocumentToolApplication tools = new ExternalDocumentToolApplication(root.resolve("artifacts"));
        try (GenerationJobApplication application = new GenerationJobApplication("tenant-a", tools, repository)) {
            ObjectNode request = documentRequest();
            GenerationJobRecord created = application.submit("alice", "trace-1", "same-request", request);
            GenerationJobRecord duplicate = application.submit("alice", "trace-2", "same-request",
                    reorderedDocumentRequest(request));
            assertEquals(created.getJobId(), duplicate.getJobId());

            GenerationJobRecord completed = awaitTerminal(application, created.getJobId());
            assertEquals(GenerationJobStatus.SUCCEEDED, completed.getStatus());
            assertEquals(1, completed.getAttemptCount());
            assertEquals(1, completed.getArtifacts().size());
            assertEquals("text/html", completed.getArtifacts().get(0).getMediaType());
            assertTrue(completed.getArtifacts().get(0).getResourceUri().startsWith("omni-office://artifacts/"));

            ObjectNode changed = request.deepCopy();
            changed.put("outputFormat", "PDF");
            assertThrows(GenerationJobConflictException.class,
                    () -> application.submit("alice", null, "same-request", changed));
        }

        FileGenerationJobRepository reopened = new FileGenerationJobRepository(root.resolve("jobs"));
        assertEquals(1, reopened.list(10).size());
        assertEquals(GenerationJobStatus.SUCCEEDED, reopened.list(10).get(0).getStatus());
    }

    @Test
    void recoversPersistedNonTerminalJobAndKeepsFailedJobsInspectable() throws Exception {
        Path root = Files.createTempDirectory("generation-recovery");
        FileGenerationJobRepository repository = new FileGenerationJobRepository(root.resolve("jobs"));
        GenerationJobRecord queued = record(documentRequest());
        repository.create(queued);

        ExternalDocumentToolApplication tools = new ExternalDocumentToolApplication(root.resolve("artifacts"));
        String invalidJobId;
        try (GenerationJobApplication application = new GenerationJobApplication("tenant-a", tools, repository)) {
            GenerationJobRecord recovered = awaitTerminal(application, queued.getJobId());
            assertEquals(GenerationJobStatus.SUCCEEDED, recovered.getStatus());
            assertEquals(1, recovered.getAttemptCount());

            ObjectNode invalid = documentRequest();
            ((ObjectNode) invalid.path("documentSpec")).remove("sections");
            GenerationJobRecord invalidPersisted = record(invalid);
            invalidPersisted.setJobId(UUID.randomUUID().toString());
            invalidPersisted.setStatus(GenerationJobStatus.QUEUED);
            repository.create(invalidPersisted);
            invalidJobId = invalidPersisted.getJobId();
            // A separately constructed application represents the next process recovering persisted work.
        }
        try (GenerationJobApplication recoveredApplication = new GenerationJobApplication(
                "tenant-a", tools, repository)) {
            GenerationJobRecord failed = awaitTerminal(recoveredApplication, invalidJobId);
            assertEquals(GenerationJobStatus.FAILED, failed.getStatus());
            assertEquals(2, failed.getAttemptCount());
            assertEquals("GENERATION_DOCUMENT_VALIDATION_FAILED", failed.getErrorCode());
            assertFalse(failed.getErrorMessage().isBlank());
        }
    }

    @Test
    void cancelsQueuedJobWithoutLettingWorkerOverwriteTerminalState() throws Exception {
        Path root = Files.createTempDirectory("generation-cancel");
        FileGenerationJobRepository repository = new FileGenerationJobRepository(root.resolve("jobs"));
        ExternalDocumentToolApplication tools = new ExternalDocumentToolApplication(root.resolve("artifacts"));
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch release = new CountDownLatch(1);
        executor.submit(() -> {
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        try (GenerationJobApplication application = new GenerationJobApplication(
                "tenant-a", tools, repository, executor, false, Clock.systemUTC(), mapper,
                new NoopGenerationEventPublisher())) {
            GenerationJobRecord queued = application.submit("alice", null, null, documentRequest());
            assertThrows(IllegalArgumentException.class,
                    () -> application.get(queued.getJobId(), "bob", false));
            assertThrows(IllegalArgumentException.class,
                    () -> application.cancel(queued.getJobId(), "bob", false));
            assertTrue(application.listForPrincipal("bob", null, null, 10).getJobs().isEmpty());
            assertEquals(1, application.listForPrincipal("alice", null, null, 10).getJobs().size());
            GenerationJobRecord cancelled = application.cancel(queued.getJobId(), "alice", false);
            assertEquals(GenerationJobStatus.CANCELLED, cancelled.getStatus());
            assertEquals("CANCELLED_BY_CALLER", cancelled.getErrorCode());
            release.countDown();
            Thread.sleep(50);
            assertEquals(GenerationJobStatus.CANCELLED, application.get(queued.getJobId()).getStatus());
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void executesFreeformAndTemplateAiInsideThePersistentGenerationJobBoundary() throws Exception {
        Path root = Files.createTempDirectory("generation-ai-jobs");
        FileGenerationJobRepository repository = new FileGenerationJobRepository(root.resolve("jobs"));
        ExternalDocumentToolApplication tools = new ExternalDocumentToolApplication(root.resolve("artifacts"));
        try (InputStream template = getClass().getResourceAsStream(
                "/document-template/1.0/example-assessment-template.json")) {
            tools.registerTemplate(template);
        }
        String documentOutput;
        String templateOutput;
        try (InputStream input = getClass().getResourceAsStream("/document-spec/1.0/example-simple.json")) {
            documentOutput = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        try (InputStream input = getClass().getResourceAsStream(
                "/document-template/1.0/example-assessment-data.json")) {
            templateOutput = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        AtomicInteger calls = new AtomicInteger();
        InternalAiDocumentService ai = tools.createInternalAiService(request ->
                calls.getAndIncrement() == 0 ? documentOutput : templateOutput);

        try (GenerationJobApplication application = new GenerationJobApplication(
                "tenant-a", tools, repository, new NoopGenerationEventPublisher(),
                GenerationQuota.unlimited(), ai)) {
            ObjectNode freeform = mapper.createObjectNode();
            freeform.put("mode", "AI_FREEFORM");
            freeform.put("outputFormat", "HTML");
            freeform.put("instruction", "生成系统评估报告");
            freeform.putObject("context").put("system", "omni-office");
            GenerationJobRecord freeformResult = awaitTerminal(application,
                    application.submit("alice", "ai-1", "ai-freeform", freeform).getJobId());
            assertEquals(GenerationJobStatus.SUCCEEDED, freeformResult.getStatus());
            assertEquals(GenerationMode.AI_FREEFORM, freeformResult.getMode());
            assertEquals(1, freeformResult.getMaxAttempts());
            assertEquals("text/html", freeformResult.getArtifacts().get(0).getMediaType());

            ObjectNode templated = mapper.createObjectNode();
            templated.put("mode", "AI_TEMPLATE");
            templated.put("outputFormat", "HTML");
            templated.put("templateId", "system.assessment");
            templated.put("templateVersion", "1.0.0");
            templated.put("instruction", "根据上下文填充评估模板");
            GenerationJobRecord templateResult = awaitTerminal(application,
                    application.submit("alice", "ai-2", "ai-template", templated).getJobId());
            assertEquals(GenerationJobStatus.SUCCEEDED, templateResult.getStatus());
            assertEquals(GenerationMode.AI_TEMPLATE, templateResult.getMode());
            assertEquals(2, calls.get());
        }
    }

    @Test
    void rejectsAiModeBeforePersistenceWhenInternalModelIsDisabled() throws Exception {
        Path root = Files.createTempDirectory("generation-ai-disabled");
        try (GenerationJobApplication application = new GenerationJobApplication("tenant-a",
                new ExternalDocumentToolApplication(root.resolve("artifacts")),
                new FileGenerationJobRepository(root.resolve("jobs")))) {
            ObjectNode request = mapper.createObjectNode();
            request.put("mode", "AI_FREEFORM");
            request.put("outputFormat", "HTML");
            request.put("instruction", "生成报告");
            assertThrows(IllegalStateException.class,
                    () -> application.submit("alice", null, null, request));
            assertTrue(application.list(10).isEmpty());
        }
    }

    @Test
    void pausesRequiredAiReviewAndContinuesOnlyAfterDifferentReviewerApproves() throws Exception {
        Path root = Files.createTempDirectory("generation-ai-review");
        ExternalDocumentToolApplication tools = new ExternalDocumentToolApplication(root.resolve("artifacts"));
        String documentOutput;
        try (InputStream input = getClass().getResourceAsStream("/document-spec/1.0/example-simple.json")) {
            documentOutput = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        InternalAiDocumentService ai = tools.createInternalAiService(request -> documentOutput);
        try (GenerationJobApplication application = new GenerationJobApplication(
                "tenant-a", tools, new FileGenerationJobRepository(root.resolve("jobs")),
                new NoopGenerationEventPublisher(), GenerationQuota.unlimited(), ai)) {
            ObjectNode request = mapper.createObjectNode();
            request.put("mode", "AI_FREEFORM");
            request.put("outputFormat", "HTML");
            request.put("instruction", "生成待审核报告");
            request.put("reviewPolicy", "REQUIRED");
            GenerationJobRecord pending = awaitStatus(application,
                    application.submit("alice", null, null, request).getJobId(),
                    GenerationJobStatus.PENDING_REVIEW);
            assertEquals(GenerationStage.AI_REVIEW, pending.getCurrentStage());
            assertTrue(pending.getRequest().path("redacted").asBoolean(false)
                    || !pending.getRequest().has("instruction"));
            assertThrows(IllegalArgumentException.class,
                    () -> application.approveReview(pending.getJobId(), "alice", null));

            GenerationJobRecord approved = application.approveReview(
                    pending.getJobId(), "reviewer", "结构已核对");
            assertEquals(GenerationJobStatus.QUEUED, approved.getStatus());
            assertEquals(GenerationMode.AI_FREEFORM, approved.getMode());
            GenerationJobRecord completed = awaitTerminal(application, approved.getJobId());
            assertEquals(GenerationJobStatus.SUCCEEDED, completed.getStatus());
            assertEquals(GenerationMode.AI_FREEFORM, completed.getMode());
            assertEquals(GenerationStage.COMPLETED, completed.getCurrentStage());
            assertTrue(completed.getRequest().path("redacted").asBoolean());

            GenerationJobRecord expiring = awaitStatus(application,
                    application.submit("alice", null, null, request).getJobId(),
                    GenerationJobStatus.PENDING_REVIEW);
            assertTrue(expiring.getDeadlineAt().isAfter(expiring.getStageStartedAt()));
            assertEquals(1, application.expirePendingReviews(expiring.getDeadlineAt()));
            GenerationJobRecord expired = application.get(expiring.getJobId());
            assertEquals(GenerationJobStatus.FAILED, expired.getStatus());
            assertEquals("AI_REVIEW_EXPIRED", expired.getErrorCode());
        }
    }

    private GenerationJobRecord record(JsonNode request) {
        Instant now = Instant.now();
        GenerationJobRecord value = new GenerationJobRecord();
        value.setJobId(UUID.randomUUID().toString());
        value.setTenantId("tenant-a");
        value.setPrincipalId("alice");
        value.setCorrelationId("recover-trace");
        value.setRequestSha256("test");
        value.setMode(GenerationMode.DOCUMENT_SPEC);
        value.setRequest(request);
        value.setStatus(GenerationJobStatus.RUNNING);
        value.setCreatedAt(now);
        value.setUpdatedAt(now);
        return value;
    }

    private ObjectNode documentRequest() throws Exception {
        ObjectNode value = mapper.createObjectNode();
        value.put("mode", "DOCUMENT_SPEC");
        value.put("outputFormat", "HTML");
        try (InputStream input = getClass().getResourceAsStream("/document-spec/1.0/example-simple.json")) {
            value.set("documentSpec", mapper.readTree(input));
        }
        return value;
    }

    private ObjectNode reorderedDocumentRequest(ObjectNode source) {
        ObjectNode value = mapper.createObjectNode();
        value.set("documentSpec", source.path("documentSpec").deepCopy());
        value.put("outputFormat", source.path("outputFormat").asText());
        value.put("mode", source.path("mode").asText());
        return value;
    }

    private GenerationJobRecord awaitTerminal(GenerationJobApplication application, String jobId)
            throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(20));
        GenerationJobRecord value;
        do {
            value = application.get(jobId);
            if (value.getStatus().isTerminal()) return value;
            Thread.sleep(20);
        } while (Instant.now().isBefore(deadline));
        throw new AssertionError("generation job did not complete: " + value.getStatus());
    }

    private GenerationJobRecord awaitStatus(GenerationJobApplication application, String jobId,
                                            GenerationJobStatus status) throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(20));
        GenerationJobRecord value;
        do {
            value = application.get(jobId);
            if (value.getStatus() == status) return value;
            Thread.sleep(20);
        } while (Instant.now().isBefore(deadline));
        throw new AssertionError("generation job did not reach " + status + ": " + value.getStatus());
    }
}
