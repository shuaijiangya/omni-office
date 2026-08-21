package cn.bugstack.application.generation.webhook;

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

/** 单实例开发模式的原子文件 Outbox；接口语义可由 PostgreSQL SKIP LOCKED 实现替换。 */
public final class FileWebhookDeliveryRepository implements WebhookDeliveryRepository {

    private final Path root;
    private final ObjectMapper mapper;

    /**
     * 创建文件型 Webhook Outbox。
     *
     * @param root 事件 JSON 文件目录
     */
    public FileWebhookDeliveryRepository(Path root) {
        if (root == null) throw new IllegalArgumentException("webhook outbox root is required");
        this.root = root.toAbsolutePath().normalize();
        this.mapper = new ObjectMapper().registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .setSerializationInclusion(JsonInclude.Include.NON_NULL);
        try {
            Files.createDirectories(this.root);
        } catch (IOException e) {
            throw new IllegalStateException("failed to create webhook outbox", e);
        }
    }

    @Override
    public synchronized WebhookDeliveryRecord enqueue(WebhookDeliveryRecord record) {
        validate(record);
        Optional<WebhookDeliveryRecord> existing = findByEventKey(
                record.getTenantId(), record.getJobId(), record.getEventType());
        if (existing.isPresent()) return existing.get();
        Path target = path(record.getEventId());
        if (Files.exists(target)) throw new IllegalStateException("webhook event already exists");
        WebhookDeliveryRecord value = copy(record);
        value.setVersion(1L);
        write(value, target, true);
        return copy(value);
    }

    @Override
    public synchronized WebhookDeliveryRecord save(WebhookDeliveryRecord record) {
        validate(record);
        Path target = path(record.getEventId());
        if (!Files.isRegularFile(target)) throw new IllegalArgumentException("webhook event does not exist");
        WebhookDeliveryRecord current = read(target);
        requireVersion(record, current);
        WebhookDeliveryRecord value = copy(record);
        value.setVersion(current.getVersion() + 1L);
        write(value, target, false);
        return copy(value);
    }

    @Override
    public synchronized Optional<WebhookDeliveryRecord> findByEventKey(String tenantId, String jobId,
                                                                        String eventType) {
        return readAll().stream().filter(item -> tenantId.equals(item.getTenantId()))
                .filter(item -> jobId.equals(item.getJobId()))
                .filter(item -> eventType.equals(item.getEventType())).findFirst();
    }

    @Override
    public synchronized List<WebhookDeliveryRecord> claimDue(String workerId, Instant now,
                                                              Instant leaseUntil, int limit) {
        validateLease(workerId, now, leaseUntil);
        if (limit < 1) throw new IllegalArgumentException("webhook due query is invalid");
        List<WebhookDeliveryRecord> result = new ArrayList<>();
        for (WebhookDeliveryRecord value : readAll()) {
            if (!value.getStatus().isTerminal() && value.getNextAttemptAt() != null
                    && !value.getNextAttemptAt().isAfter(now)
                    && (value.getLeaseUntil() == null || !value.getLeaseUntil().isAfter(now))) {
                result.add(value);
            }
        }
        result.sort(Comparator.comparing(WebhookDeliveryRecord::getNextAttemptAt)
                .thenComparing(WebhookDeliveryRecord::getCreatedAt));
        List<WebhookDeliveryRecord> claimed = new ArrayList<>();
        for (WebhookDeliveryRecord value : result.subList(0, Math.min(limit, result.size()))) {
            value.setLeaseOwner(workerId);
            value.setLeaseUntil(leaseUntil);
            value.setUpdatedAt(now);
            value.setVersion(value.getVersion() + 1L);
            write(value, path(value.getEventId()), false);
            claimed.add(copy(value));
        }
        return claimed;
    }

    @Override
    public synchronized WebhookDeliveryRecord saveClaimed(WebhookDeliveryRecord record,
                                                           String workerId, Instant now) {
        validate(record);
        Path target = path(record.getEventId());
        if (!Files.isRegularFile(target)) throw new IllegalArgumentException("webhook event does not exist");
        WebhookDeliveryRecord current = read(target);
        if (!workerId.equals(current.getLeaseOwner()) || current.getLeaseUntil() == null
                || !current.getLeaseUntil().isAfter(now)) {
            throw new IllegalStateException("webhook delivery lease is not owned by this worker");
        }
        requireVersion(record, current);
        WebhookDeliveryRecord value = copy(record);
        value.setLeaseOwner(null);
        value.setLeaseUntil(null);
        value.setVersion(current.getVersion() + 1L);
        write(value, target, false);
        return copy(value);
    }

    @Override
    public synchronized List<WebhookDeliveryRecord> list(String tenantId, int limit) {
        if (tenantId == null || limit < 1) throw new IllegalArgumentException("webhook list query is invalid");
        List<WebhookDeliveryRecord> result = new ArrayList<>();
        readAll().stream().filter(item -> tenantId.equals(item.getTenantId())).forEach(result::add);
        result.sort(Comparator.comparing(WebhookDeliveryRecord::getCreatedAt).reversed());
        return new ArrayList<>(result.subList(0, Math.min(limit, result.size())));
    }

    @Override
    public synchronized Map<WebhookDeliveryStatus, Long> countsByStatus() {
        Map<WebhookDeliveryStatus, Long> result = new EnumMap<>(WebhookDeliveryStatus.class);
        for (WebhookDeliveryStatus status : WebhookDeliveryStatus.values()) result.put(status, 0L);
        for (WebhookDeliveryRecord record : readAll()) {
            result.put(record.getStatus(), result.get(record.getStatus()) + 1L);
        }
        return result;
    }

    private List<WebhookDeliveryRecord> readAll() {
        List<WebhookDeliveryRecord> result = new ArrayList<>();
        try (Stream<Path> paths = Files.list(root)) {
            paths.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .forEach(path -> result.add(read(path)));
        } catch (IOException e) {
            throw new IllegalStateException("failed to list webhook outbox", e);
        }
        return result;
    }

    private WebhookDeliveryRecord read(Path path) {
        try {
            WebhookDeliveryRecord value = mapper.readValue(path.toFile(), WebhookDeliveryRecord.class);
            validate(value);
            return value;
        } catch (IOException e) {
            throw new IllegalStateException("failed to read webhook event", e);
        }
    }

    private void write(WebhookDeliveryRecord value, Path target, boolean create) {
        try {
            Path temporary = Files.createTempFile(root, ".webhook-", ".tmp");
            try {
                mapper.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), value);
                if (create && Files.exists(target)) throw new IllegalStateException("webhook event already exists");
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
            throw new IllegalStateException("failed to persist webhook event", e);
        }
    }

    private Path path(String eventId) {
        if (eventId == null || !eventId.matches("[0-9a-f-]{36}")) {
            throw new IllegalArgumentException("webhook event id is invalid");
        }
        Path value = root.resolve(eventId + ".json").normalize();
        if (!root.equals(value.getParent())) throw new IllegalArgumentException("webhook path escapes root");
        return value;
    }

    private void validate(WebhookDeliveryRecord value) {
        if (value == null || value.getEventId() == null || value.getEventType() == null
                || value.getTenantId() == null || value.getWebhookId() == null || value.getJobId() == null
                || value.getPayload() == null || value.getStatus() == null || value.getCreatedAt() == null
                || value.getUpdatedAt() == null || value.getMaxAttempts() < 1) {
            throw new IllegalArgumentException("webhook delivery record is incomplete");
        }
        path(value.getEventId());
    }

    private void requireVersion(WebhookDeliveryRecord requested, WebhookDeliveryRecord current) {
        if (requested.getVersion() != current.getVersion()) {
            throw new IllegalStateException("webhook delivery was modified concurrently");
        }
    }

    private void validateLease(String workerId, Instant now, Instant leaseUntil) {
        if (workerId == null || !workerId.matches("[A-Za-z0-9._:-]{1,128}")
                || now == null || leaseUntil == null || !leaseUntil.isAfter(now)) {
            throw new IllegalArgumentException("webhook delivery lease is invalid");
        }
    }

    private WebhookDeliveryRecord copy(WebhookDeliveryRecord value) {
        return mapper.convertValue(mapper.valueToTree(value), WebhookDeliveryRecord.class);
    }
}
