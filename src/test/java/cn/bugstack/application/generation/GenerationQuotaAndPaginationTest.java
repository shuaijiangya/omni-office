package cn.bugstack.application.generation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GenerationQuotaAndPaginationTest {

    @Test
    void enforcesFileQuotaAndFiltersStablePagesWithTimestampTies() throws Exception {
        FileGenerationJobRepository repository = new FileGenerationJobRepository(
                Files.createTempDirectory("generation-quota-page"));
        Instant now = Instant.parse("2026-08-21T12:00:00Z");
        GenerationQuota quota = new GenerationQuota(2, 3);
        GenerationJobRecord first = repository.create(job(now, GenerationJobStatus.QUEUED), quota,
                Instant.parse("2026-08-21T00:00:00Z"));
        GenerationJobRecord second = repository.create(job(now, GenerationJobStatus.QUEUED), quota,
                Instant.parse("2026-08-21T00:00:00Z"));
        assertThrows(GenerationQuotaExceededException.class, () -> repository.create(
                job(now.plusSeconds(1), GenerationJobStatus.QUEUED), quota,
                Instant.parse("2026-08-21T00:00:00Z")));

        second.setStatus(GenerationJobStatus.SUCCEEDED);
        second.setUpdatedAt(now.plusSeconds(2));
        repository.save(second);
        GenerationJobRecord third = repository.create(job(now.plusSeconds(1), GenerationJobStatus.QUEUED),
                quota, Instant.parse("2026-08-21T00:00:00Z"));
        assertThrows(GenerationQuotaExceededException.class, () -> repository.create(
                job(now.plusSeconds(2), GenerationJobStatus.QUEUED), quota,
                Instant.parse("2026-08-21T00:00:00Z")));

        List<GenerationJobRecord> firstPage = repository.list(null, null, null, 2);
        assertEquals(List.of(third.getJobId(), max(first.getJobId(), second.getJobId())),
                List.of(firstPage.get(0).getJobId(), firstPage.get(1).getJobId()));
        GenerationJobRecord cursor = firstPage.get(1);
        List<GenerationJobRecord> nextPage = repository.list(null, cursor.getCreatedAt(),
                cursor.getJobId(), 2);
        assertEquals(1, nextPage.size());
        assertEquals(min(first.getJobId(), second.getJobId()), nextPage.get(0).getJobId());
        assertEquals(1, repository.list(GenerationJobStatus.SUCCEEDED, null, null, 10).size());
    }

    @Test
    void loadsStrictTenantQuotaConfiguration() throws Exception {
        Path path = Files.createTempFile("generation-quota", ".json").toAbsolutePath();
        Files.writeString(path, "{\"default\":{\"maxActiveJobs\":5,\"maxJobsPerDay\":20},"
                + "\"tenants\":{\"tenant-a\":{\"maxActiveJobs\":2,\"maxJobsPerDay\":3}}}");
        JsonGenerationQuotaPolicy policy = new JsonGenerationQuotaPolicy(path);
        assertEquals(2, policy.quota("tenant-a").getMaxActiveJobs());
        assertEquals(20, policy.quota("tenant-b").getMaxJobsPerDay());
    }

    private GenerationJobRecord job(Instant now, GenerationJobStatus status) {
        GenerationJobRecord value = new GenerationJobRecord();
        value.setJobId(UUID.randomUUID().toString());
        value.setTenantId("tenant-a");
        value.setPrincipalId("alice");
        value.setCorrelationId("trace");
        value.setRequestSha256("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        value.setMode(GenerationMode.DOCUMENT_SPEC);
        value.setRequest(new ObjectMapper().createObjectNode().put("mode", "DOCUMENT_SPEC"));
        value.setStatus(status);
        value.setCreatedAt(now);
        value.setUpdatedAt(now);
        return value;
    }

    private String max(String left, String right) { return left.compareTo(right) > 0 ? left : right; }
    private String min(String left, String right) { return left.compareTo(right) < 0 ? left : right; }
}
