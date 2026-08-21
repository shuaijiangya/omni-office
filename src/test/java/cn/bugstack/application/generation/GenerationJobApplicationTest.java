package cn.bugstack.application.generation;

import cn.bugstack.application.external.ExternalDocumentToolApplication;
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
            assertEquals("GENERATION_FAILED", failed.getErrorCode());
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
            GenerationJobRecord cancelled = application.cancel(queued.getJobId());
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
}
