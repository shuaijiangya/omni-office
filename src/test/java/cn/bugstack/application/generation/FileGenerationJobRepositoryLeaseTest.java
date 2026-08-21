package cn.bugstack.application.generation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileGenerationJobRepositoryLeaseTest {

    @Test
    void preventsConcurrentClaimAndRecoversOnlyAfterLeaseExpiry() throws Exception {
        FileGenerationJobRepository repository = new FileGenerationJobRepository(
                Files.createTempDirectory("generation-lease"));
        Instant now = Instant.parse("2026-08-21T00:00:00Z");
        GenerationJobRecord created = repository.create(record(now));
        assertEquals(1L, created.getVersion());

        GenerationJobRecord first = repository.claimNext("worker-a", now, now.plusSeconds(60)).orElseThrow();
        assertEquals(GenerationJobStatus.RUNNING, first.getStatus());
        assertEquals(1, first.getAttemptCount());
        assertFalse(repository.claimNext("worker-b", now.plusSeconds(30), now.plusSeconds(90)).isPresent());

        assertTrue(repository.renewLease(first.getJobId(), "worker-a", now.plusSeconds(30),
                now.plusSeconds(90)));
        assertThrows(GenerationJobConflictException.class,
                () -> repository.saveClaimed(first, "worker-a"));
        assertFalse(repository.claimNext("worker-b", now.plusSeconds(61), now.plusSeconds(121)).isPresent());

        GenerationJobRecord recovered = repository.claimNext(
                "worker-b", now.plusSeconds(91), now.plusSeconds(151)).orElseThrow();
        assertEquals(2, recovered.getAttemptCount());
        assertEquals("worker-b", recovered.getLeaseOwner());
        assertThrows(GenerationJobConflictException.class,
                () -> repository.saveClaimed(recovered, "worker-a"));

        recovered.setStatus(GenerationJobStatus.SUCCEEDED);
        recovered.setCompletedAt(now.plusSeconds(92));
        recovered.setUpdatedAt(now.plusSeconds(92));
        GenerationJobRecord completed = repository.saveClaimed(recovered, "worker-b");
        assertEquals(GenerationJobStatus.SUCCEEDED, completed.getStatus());
        assertNull(completed.getLeaseOwner());
        assertNull(completed.getLeaseUntil());
    }

    @Test
    void claimsExpiredFinalAttemptForTerminalRecoveryWithoutExecutingAgain() throws Exception {
        FileGenerationJobRepository repository = new FileGenerationJobRepository(
                Files.createTempDirectory("generation-exhausted"));
        Instant now = Instant.parse("2026-08-21T00:00:00Z");
        GenerationJobRecord value = record(now.minusSeconds(120));
        value.setStatus(GenerationJobStatus.RUNNING);
        value.setAttemptCount(value.getMaxAttempts());
        value.setLeaseOwner("dead-worker");
        value.setLeaseUntil(now.minusSeconds(1));
        GenerationJobRecord created = repository.create(value);

        assertFalse(repository.claimNext("worker-b", now, now.plusSeconds(60)).isPresent());
        GenerationJobRecord exhausted = repository.claimExhausted(
                "worker-b", now, now.plusSeconds(60)).orElseThrow();
        assertEquals(created.getAttemptCount(), exhausted.getAttemptCount());
        assertEquals("worker-b", exhausted.getLeaseOwner());
    }

    private GenerationJobRecord record(Instant now) {
        GenerationJobRecord value = new GenerationJobRecord();
        value.setJobId(UUID.randomUUID().toString());
        value.setTenantId("tenant-a");
        value.setPrincipalId("alice");
        value.setCorrelationId("trace");
        value.setRequestSha256("hash");
        value.setMode(GenerationMode.DOCUMENT_SPEC);
        value.setRequest(new ObjectMapper().createObjectNode().put("mode", "DOCUMENT_SPEC"));
        value.setStatus(GenerationJobStatus.QUEUED);
        value.setMaxAttempts(3);
        value.setCreatedAt(now);
        value.setUpdatedAt(now);
        return value;
    }
}
