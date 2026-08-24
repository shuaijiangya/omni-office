package cn.bugstack.application.generation;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.EnumMap;
import java.util.Map;
import java.util.stream.Stream;

/** 单租户原子文件任务仓库，作为开发模式和 PostgreSQL 实现的参考语义。 */
public final class FileGenerationJobRepository implements GenerationJobRepository {

    private final Path root;
    private final ObjectMapper mapper;

    /**
     * 创建单租户文件任务仓储。
     *
     * @param root 任务 JSON 文件目录
     */
    public FileGenerationJobRepository(Path root) {
        if (root == null) throw new IllegalArgumentException("generation job root is required");
        this.root = root.toAbsolutePath().normalize();
        this.mapper = new ObjectMapper().registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .setSerializationInclusion(JsonInclude.Include.NON_NULL);
        try {
            Files.createDirectories(this.root);
        } catch (IOException e) {
            throw new IllegalStateException("failed to create generation job repository", e);
        }
    }

    @Override
    public synchronized GenerationJobRecord create(GenerationJobRecord record, GenerationQuota quota,
                                                    Instant dayStart) {
        if (record != null && record.getCurrentStage() == null) {
            record.setCurrentStage(GenerationStage.QUEUED);
        }
        validate(record);
        validateQuota(quota, dayStart);
        Path target = path(record.getJobId());
        if (Files.exists(target)) throw new IllegalStateException("generation job already exists");
        List<GenerationJobRecord> existing = list(Integer.MAX_VALUE);
        long active = existing.stream().filter(item -> !item.getStatus().isTerminal()).count();
        if (active >= quota.getMaxActiveJobs()) {
            throw new GenerationQuotaExceededException("maxActiveJobs",
                    "tenant active generation job quota exceeded");
        }
        long daily = existing.stream().filter(item -> !item.getCreatedAt().isBefore(dayStart)).count();
        if (daily >= quota.getMaxJobsPerDay()) {
            throw new GenerationQuotaExceededException("maxJobsPerDay",
                    "tenant daily generation job quota exceeded");
        }
        GenerationJobRecord value = copy(record);
        value.setVersion(1L);
        write(value, target, true);
        return copy(value);
    }

    @Override
    public synchronized GenerationJobRecord save(GenerationJobRecord record) {
        validate(record);
        Path target = path(record.getJobId());
        if (!Files.isRegularFile(target)) throw new IllegalArgumentException("generation job does not exist");
        GenerationJobRecord current = read(target);
        requireVersion(record, current);
        GenerationJobRecord value = copy(record);
        value.setVersion(current.getVersion() + 1L);
        write(value, target, false);
        return copy(value);
    }

    @Override
    public synchronized Optional<GenerationJobRecord> claimNext(String workerId, Instant now,
                                                                 Instant leaseUntil) {
        validateLease(workerId, now, leaseUntil);
        Optional<GenerationJobRecord> candidate = list(Integer.MAX_VALUE).stream()
                .filter(item -> item.getAttemptCount() < item.getMaxAttempts())
                .filter(item -> item.getStatus() == GenerationJobStatus.QUEUED
                        || (item.getStatus() == GenerationJobStatus.RUNNING
                        && (item.getLeaseUntil() == null || !item.getLeaseUntil().isAfter(now))))
                .min(Comparator.comparing(GenerationJobRecord::getCreatedAt));
        if (candidate.isEmpty()) return Optional.empty();
        GenerationJobRecord value = candidate.get();
        value.setStatus(GenerationJobStatus.RUNNING);
        value.setAttemptCount(value.getAttemptCount() + 1);
        if (value.getStartedAt() == null) value.setStartedAt(now);
        value.setLeaseOwner(workerId);
        value.setLeaseUntil(leaseUntil);
        value.setUpdatedAt(now);
        value.setVersion(value.getVersion() + 1L);
        write(value, path(value.getJobId()), false);
        return Optional.of(copy(value));
    }

    @Override
    public synchronized Optional<GenerationJobRecord> claimExhausted(String workerId, Instant now,
                                                                      Instant leaseUntil) {
        validateLease(workerId, now, leaseUntil);
        Optional<GenerationJobRecord> candidate = list(Integer.MAX_VALUE).stream()
                .filter(item -> item.getAttemptCount() >= item.getMaxAttempts())
                .filter(item -> item.getStatus() == GenerationJobStatus.QUEUED
                        || (item.getStatus() == GenerationJobStatus.RUNNING
                        && (item.getLeaseUntil() == null || !item.getLeaseUntil().isAfter(now))))
                .min(Comparator.comparing(GenerationJobRecord::getCreatedAt));
        if (candidate.isEmpty()) return Optional.empty();
        GenerationJobRecord value = candidate.get();
        value.setStatus(GenerationJobStatus.RUNNING);
        value.setLeaseOwner(workerId);
        value.setLeaseUntil(leaseUntil);
        value.setUpdatedAt(now);
        value.setVersion(value.getVersion() + 1L);
        write(value, path(value.getJobId()), false);
        return Optional.of(copy(value));
    }

    @Override
    public synchronized GenerationJobRecord saveClaimed(GenerationJobRecord record, String workerId) {
        validate(record);
        Path target = path(record.getJobId());
        if (!Files.isRegularFile(target)) throw new IllegalArgumentException("generation job does not exist");
        GenerationJobRecord current = read(target);
        if (current.getStatus() != GenerationJobStatus.RUNNING
                || !workerId.equals(current.getLeaseOwner())
                || current.getLeaseUntil() == null
                || !current.getLeaseUntil().isAfter(record.getUpdatedAt())) {
            throw new GenerationJobConflictException("generation job lease is not owned by this worker");
        }
        requireVersion(record, current);
        GenerationJobRecord value = copy(record);
        if (value.getStatus() != GenerationJobStatus.RUNNING) {
            value.setLeaseOwner(null);
            value.setLeaseUntil(null);
        }
        value.setVersion(current.getVersion() + 1L);
        write(value, target, false);
        return copy(value);
    }

    @Override
    public synchronized boolean renewLease(String jobId, String workerId, Instant now,
                                           Instant leaseUntil) {
        validateLease(workerId, now, leaseUntil);
        Path target = path(jobId);
        if (!Files.isRegularFile(target)) return false;
        GenerationJobRecord current = read(target);
        if (current.getStatus() != GenerationJobStatus.RUNNING
                || !workerId.equals(current.getLeaseOwner()) || current.getLeaseUntil() == null
                || !current.getLeaseUntil().isAfter(now)) return false;
        current.setLeaseUntil(leaseUntil);
        current.setUpdatedAt(now);
        current.setVersion(current.getVersion() + 1L);
        write(current, target, false);
        return true;
    }

    @Override
    public synchronized Optional<GenerationJobRecord> find(String jobId) {
        Path target = path(jobId);
        return Files.isRegularFile(target) ? Optional.of(read(target)) : Optional.empty();
    }

    @Override
    public synchronized Optional<GenerationJobRecord> findByIdempotencyKey(String principalId,
                                                                            String idempotencyKey) {
        if (principalId == null || idempotencyKey == null) return Optional.empty();
        return list(Integer.MAX_VALUE).stream()
                .filter(item -> principalId.equals(item.getPrincipalId()))
                .filter(item -> idempotencyKey.equals(item.getIdempotencyKey()))
                .findFirst();
    }

    @Override
    public synchronized List<GenerationJobRecord> list(GenerationJobStatus status,
                                                        Instant beforeCreatedAt,
                                                        String beforeJobId, int limit) {
        return listForPrincipalInternal(null, status, beforeCreatedAt, beforeJobId, limit);
    }

    @Override
    public synchronized List<GenerationJobRecord> listForPrincipal(String principalId,
                                                                   GenerationJobStatus status,
                                                                   Instant beforeCreatedAt,
                                                                   String beforeJobId, int limit) {
        if (principalId == null || !principalId.matches("[A-Za-z0-9._-]{1,64}")) {
            throw new IllegalArgumentException("generation principal id is invalid");
        }
        return listForPrincipalInternal(principalId, status, beforeCreatedAt, beforeJobId, limit);
    }

    private List<GenerationJobRecord> listForPrincipalInternal(String principalId,
                                                               GenerationJobStatus status,
                                                               Instant beforeCreatedAt,
                                                               String beforeJobId, int limit) {
        if (limit < 1) throw new IllegalArgumentException("generation job list limit must be positive");
        if ((beforeCreatedAt == null) != (beforeJobId == null)) {
            throw new IllegalArgumentException("generation job cursor is incomplete");
        }
        if (beforeJobId != null) path(beforeJobId);
        List<GenerationJobRecord> result = new ArrayList<>();
        try (Stream<Path> paths = Files.list(root)) {
            paths.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .forEach(path -> result.add(read(path)));
        } catch (IOException e) {
            throw new IllegalStateException("failed to list generation jobs", e);
        }
        Comparator<GenerationJobRecord> ordering = Comparator
                .comparing(GenerationJobRecord::getCreatedAt)
                .thenComparing(GenerationJobRecord::getJobId).reversed();
        result.removeIf(item -> principalId != null && !principalId.equals(item.getPrincipalId()));
        result.removeIf(item -> status != null && item.getStatus() != status);
        if (beforeCreatedAt != null) {
            GenerationJobRecord cursor = new GenerationJobRecord();
            cursor.setCreatedAt(beforeCreatedAt);
            cursor.setJobId(beforeJobId);
            result.removeIf(item -> ordering.compare(item, cursor) <= 0);
        }
        result.sort(ordering);
        return new ArrayList<>(result.subList(0, Math.min(limit, result.size())));
    }

    @Override
    public synchronized Map<GenerationJobStatus, Long> countsByStatus() {
        Map<GenerationJobStatus, Long> result = new EnumMap<>(GenerationJobStatus.class);
        for (GenerationJobStatus status : GenerationJobStatus.values()) result.put(status, 0L);
        for (GenerationJobRecord record : list(Integer.MAX_VALUE)) {
            result.put(record.getStatus(), result.get(record.getStatus()) + 1L);
        }
        return result;
    }

    @Override
    public synchronized int purgeTerminalBefore(Instant cutoff, int limit) {
        if (cutoff == null || limit < 1 || limit > 10_000) {
            throw new IllegalArgumentException("generation purge boundary is invalid");
        }
        List<GenerationJobRecord> candidates = list(Integer.MAX_VALUE).stream()
                .filter(item -> item.getStatus().isTerminal())
                .filter(item -> !item.getUpdatedAt().isAfter(cutoff))
                .sorted(Comparator.comparing(GenerationJobRecord::getUpdatedAt))
                .limit(limit).collect(java.util.stream.Collectors.toList());
        int deleted = 0;
        for (GenerationJobRecord candidate : candidates) {
            try {
                if (Files.deleteIfExists(path(candidate.getJobId()))) deleted++;
            } catch (IOException e) {
                throw new IllegalStateException("failed to purge generation job", e);
            }
        }
        return deleted;
    }

    private GenerationJobRecord read(Path path) {
        try {
            GenerationJobRecord value = mapper.readValue(path.toFile(), GenerationJobRecord.class);
            if (value.getCurrentStage() == null) {
                value.setCurrentStage(value.getStatus() == GenerationJobStatus.SUCCEEDED
                        ? GenerationStage.COMPLETED : GenerationStage.QUEUED);
            }
            validate(value);
            return value;
        } catch (IOException e) {
            throw new IllegalStateException("failed to read generation job", e);
        }
    }

    private void write(GenerationJobRecord value, Path target, boolean create) {
        try {
            Path temporary = Files.createTempFile(root, ".generation-job-", ".tmp");
            try {
                mapper.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), value);
                if (create && Files.exists(target)) throw new IllegalStateException("generation job already exists");
                try {
                    Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE,
                            StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException e) {
            throw new IllegalStateException("failed to persist generation job", e);
        }
    }

    private Path path(String jobId) {
        if (jobId == null || !jobId.matches("[0-9a-f-]{36}")) {
            throw new IllegalArgumentException("generation job id is invalid");
        }
        Path value = root.resolve(jobId + ".json").normalize();
        if (!root.equals(value.getParent())) throw new IllegalArgumentException("generation job path escapes root");
        return value;
    }

    private void validate(GenerationJobRecord value) {
        if (value == null || value.getJobId() == null || value.getTenantId() == null
                || value.getPrincipalId() == null || value.getMode() == null || value.getRequest() == null
                || value.getStatus() == null || value.getCurrentStage() == null
                || value.getCreatedAt() == null || value.getUpdatedAt() == null) {
            throw new IllegalArgumentException("generation job record is incomplete");
        }
        path(value.getJobId());
    }

    private void requireVersion(GenerationJobRecord requested, GenerationJobRecord current) {
        if (requested.getVersion() != current.getVersion()) {
            throw new GenerationJobConflictException("generation job was modified concurrently");
        }
    }

    private void validateLease(String workerId, Instant now, Instant leaseUntil) {
        if (workerId == null || !workerId.matches("[A-Za-z0-9._:-]{1,128}")
                || now == null || leaseUntil == null || !leaseUntil.isAfter(now)) {
            throw new IllegalArgumentException("generation job lease is invalid");
        }
    }

    private void validateQuota(GenerationQuota quota, Instant dayStart) {
        if (quota == null || dayStart == null) {
            throw new IllegalArgumentException("generation quota admission is invalid");
        }
    }

    private GenerationJobRecord copy(GenerationJobRecord value) {
        return mapper.convertValue(mapper.valueToTree(value), GenerationJobRecord.class);
    }
}
