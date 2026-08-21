package cn.bugstack.application.generation;

import cn.bugstack.application.external.ExternalDocumentToolApplication;
import cn.bugstack.application.external.ExternalToolResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * M11 统一生成任务应用服务。协议适配器只负责身份与 HTTP/MCP 映射，任务执行继续复用现有外部工具门面。
 */
public final class GenerationJobApplication implements AutoCloseable {

    private final String tenantId;
    private final ExternalDocumentToolApplication tools;
    private final GenerationJobRepository repository;
    private final ExecutorService executor;
    private final boolean ownsExecutor;
    private final ScheduledExecutorService coordinator;
    private final Clock clock;
    private final ObjectMapper mapper;
    private final GenerationEventPublisher eventPublisher;
    private final String workerId;
    private final Duration leaseDuration;
    private final int maxInFlight;
    private final GenerationQuota quota;
    private final ConcurrentMap<String, Future<?>> futures = new ConcurrentHashMap<>();
    private volatile boolean closed;

    /**
     * 使用默认线程池、系统时钟、无限配额和无事件发布器创建应用服务。
     *
     * @param tenantId 租户 ID
     * @param tools 文档工具门面
     * @param repository 任务仓储
     */
    public GenerationJobApplication(String tenantId, ExternalDocumentToolApplication tools,
                                    GenerationJobRepository repository) {
        this(tenantId, tools, repository, Executors.newFixedThreadPool(2), true,
                Clock.systemUTC(), new ObjectMapper(), new NoopGenerationEventPublisher(),
                GenerationQuota.unlimited());
    }

    /**
     * 使用默认线程池、系统时钟和无限配额创建应用服务。
     *
     * @param tenantId 租户 ID
     * @param tools 文档工具门面
     * @param repository 任务仓储
     * @param eventPublisher 终态事件发布器
     */
    public GenerationJobApplication(String tenantId, ExternalDocumentToolApplication tools,
                                    GenerationJobRepository repository,
                                    GenerationEventPublisher eventPublisher) {
        this(tenantId, tools, repository, Executors.newFixedThreadPool(2), true,
                Clock.systemUTC(), new ObjectMapper(), eventPublisher, GenerationQuota.unlimited());
    }

    /**
     * 使用默认线程池和系统时钟创建应用服务。
     *
     * @param tenantId 租户 ID
     * @param tools 文档工具门面
     * @param repository 任务仓储
     * @param eventPublisher 终态事件发布器
     * @param quota 租户任务配额
     */
    public GenerationJobApplication(String tenantId, ExternalDocumentToolApplication tools,
                                    GenerationJobRepository repository,
                                    GenerationEventPublisher eventPublisher, GenerationQuota quota) {
        this(tenantId, tools, repository, Executors.newFixedThreadPool(2), true,
                Clock.systemUTC(), new ObjectMapper(), eventPublisher, quota);
    }

    GenerationJobApplication(String tenantId, ExternalDocumentToolApplication tools,
                             GenerationJobRepository repository, ExecutorService executor,
                             boolean ownsExecutor, Clock clock, ObjectMapper mapper,
                             GenerationEventPublisher eventPublisher) {
        this(tenantId, tools, repository, executor, ownsExecutor, clock, mapper, eventPublisher,
                GenerationQuota.unlimited());
    }

    GenerationJobApplication(String tenantId, ExternalDocumentToolApplication tools,
                             GenerationJobRepository repository, ExecutorService executor,
                             boolean ownsExecutor, Clock clock, ObjectMapper mapper,
                             GenerationEventPublisher eventPublisher, GenerationQuota quota) {
        this(tenantId, tools, repository, executor, ownsExecutor, clock, mapper, eventPublisher,
                Executors.newSingleThreadScheduledExecutor(runnable -> {
                    Thread thread = new Thread(runnable, "generation-job-coordinator");
                    thread.setDaemon(true);
                    return thread;
                }), Duration.ofMinutes(2), 2, quota);
    }

    GenerationJobApplication(String tenantId, ExternalDocumentToolApplication tools,
                             GenerationJobRepository repository, ExecutorService executor,
                             boolean ownsExecutor, Clock clock, ObjectMapper mapper,
                             GenerationEventPublisher eventPublisher,
                             ScheduledExecutorService coordinator, Duration leaseDuration,
                             int maxInFlight) {
        this(tenantId, tools, repository, executor, ownsExecutor, clock, mapper, eventPublisher,
                coordinator, leaseDuration, maxInFlight, GenerationQuota.unlimited());
    }

    GenerationJobApplication(String tenantId, ExternalDocumentToolApplication tools,
                             GenerationJobRepository repository, ExecutorService executor,
                             boolean ownsExecutor, Clock clock, ObjectMapper mapper,
                             GenerationEventPublisher eventPublisher,
                             ScheduledExecutorService coordinator, Duration leaseDuration,
                             int maxInFlight, GenerationQuota quota) {
        if (tenantId == null || !tenantId.matches("[A-Za-z0-9._-]{1,64}") || tools == null
                || repository == null || executor == null || clock == null || mapper == null
                || eventPublisher == null || coordinator == null || leaseDuration == null
                || leaseDuration.isZero() || leaseDuration.isNegative() || maxInFlight < 1
                || quota == null) {
            throw new IllegalArgumentException("generation job application dependencies are invalid");
        }
        this.tenantId = tenantId;
        this.tools = tools;
        this.repository = repository;
        this.executor = executor;
        this.ownsExecutor = ownsExecutor;
        this.coordinator = coordinator;
        this.clock = clock;
        this.mapper = mapper.copy();
        this.eventPublisher = eventPublisher;
        this.workerId = tenantId + ":" + UUID.randomUUID();
        this.leaseDuration = leaseDuration;
        this.maxInFlight = maxInFlight;
        this.quota = quota;
        recoverTerminalEvents();
        coordinator.scheduleWithFixedDelay(this::pollSafely, 0L, 250L, TimeUnit.MILLISECONDS);
    }

    /**
     * 校验并持久化异步生成任务。
     *
     * <p>相同主体使用相同幂等键提交相同请求时返回原任务；请求内容不同时抛出冲突异常。</p>
     *
     * @param principalId 调用主体 ID
     * @param correlationId 可选关联 ID
     * @param idempotencyKey 可选幂等键
     * @param request 生成请求 JSON
     * @return 新建或幂等复用的任务快照
     * @throws GenerationJobConflictException 幂等键对应的请求内容不一致时抛出
     * @throws GenerationQuotaExceededException 租户配额已耗尽时抛出
     */
    public synchronized GenerationJobRecord submit(String principalId, String correlationId,
                                                    String idempotencyKey, JsonNode request) {
        requirePrincipal(principalId);
        ObjectNode normalized = normalizeRequest(request);
        String key = normalizeIdempotencyKey(idempotencyKey);
        String requestHash = sha256(canonicalJson(normalized));
        if (key != null) {
            Optional<GenerationJobRecord> existing = repository.findByIdempotencyKey(principalId, key);
            if (existing.isPresent()) {
                if (!requestHash.equals(existing.get().getRequestSha256())) {
                    throw new GenerationJobConflictException(
                            "idempotency key was already used with a different generation request");
                }
                return existing.get();
            }
        }
        validateExecutableRequest(normalized);
        Instant now = clock.instant();
        GenerationJobRecord record = new GenerationJobRecord();
        record.setJobId(UUID.randomUUID().toString());
        record.setTenantId(tenantId);
        record.setPrincipalId(principalId);
        record.setCorrelationId(normalizeCorrelationId(correlationId));
        record.setIdempotencyKey(key);
        record.setRequestSha256(requestHash);
        record.setMode(GenerationMode.valueOf(normalized.path("mode").asText()));
        record.setRequest(normalized);
        record.setStatus(GenerationJobStatus.QUEUED);
        record.setMaxAttempts(normalized.path("maxAttempts").asInt(2));
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        try {
            Instant dayStart = now.atZone(java.time.ZoneOffset.UTC).toLocalDate()
                    .atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
            return repository.create(record, quota, dayStart);
        } catch (GenerationJobConflictException concurrentCreate) {
            if (key == null) throw concurrentCreate;
            Optional<GenerationJobRecord> existing = repository.findByIdempotencyKey(principalId, key);
            if (existing.isEmpty()) throw concurrentCreate;
            if (!requestHash.equals(existing.get().getRequestSha256())) {
                throw new GenerationJobConflictException(
                        "idempotency key was already used with a different generation request");
            }
            return existing.get();
        }
    }

    /**
     * 查询任务并按需恢复尚未入队的终态事件。
     *
     * @param jobId 任务 UUID
     * @return 任务快照
     * @throws IllegalArgumentException 任务不存在时抛出
     */
    public synchronized GenerationJobRecord get(String jobId) {
        GenerationJobRecord record = requireRecord(jobId);
        if (record.getStatus().isTerminal() && record.getTerminalEventId() == null
                && record.getRequest() != null && record.getRequest().has("webhookId")) {
            enqueueTerminalEvent(record);
            return requireRecord(jobId);
        }
        return record;
    }

    /**
     * 查询最新任务。
     *
     * @param limit 最大返回数量
     * @return 按创建时间和任务 ID 倒序排列的任务
     */
    public List<GenerationJobRecord> list(int limit) {
        return list(null, null, limit).getJobs();
    }

    /**
     * 按状态和不透明游标稳定分页。
     *
     * @param status 可选状态过滤条件
     * @param cursor 可选的不透明游标
     * @param limit 返回数量，最终限制在 1～100
     * @return 任务分页结果
     */
    public GenerationJobPage list(GenerationJobStatus status, String cursor, int limit) {
        int pageSize = Math.min(Math.max(limit, 1), 100);
        Cursor decoded = decodeCursor(cursor);
        List<GenerationJobRecord> values = repository.list(status,
                decoded == null ? null : decoded.createdAt,
                decoded == null ? null : decoded.jobId, pageSize + 1);
        boolean more = values.size() > pageSize;
        List<GenerationJobRecord> page = new ArrayList<>(values.subList(0,
                Math.min(pageSize, values.size())));
        String next = more ? encodeCursor(page.get(page.size() - 1)) : null;
        return new GenerationJobPage(page, next);
    }

    /**
     * 统计当前租户各状态的任务数量。
     *
     * @return 包含所有状态的数量映射
     */
    public Map<GenerationJobStatus, Long> countsByStatus() {
        Map<GenerationJobStatus, Long> result = new EnumMap<>(GenerationJobStatus.class);
        for (GenerationJobStatus status : GenerationJobStatus.values()) result.put(status, 0L);
        for (GenerationJobRecord record : repository.list(Integer.MAX_VALUE)) {
            result.put(record.getStatus(), result.get(record.getStatus()) + 1L);
        }
        return result;
    }

    /**
     * 取消尚未终结的任务。
     *
     * @param jobId 任务 UUID
     * @return 已取消任务快照
     * @throws GenerationJobConflictException 任务已经终结或持续发生并发更新时抛出
     */
    public GenerationJobRecord cancel(String jobId) {
        for (int attempt = 0; attempt < 10; attempt++) {
            GenerationJobRecord record = get(jobId);
            if (record.getStatus().isTerminal()) {
                throw new GenerationJobConflictException("generation job is already in a terminal state");
            }
            Instant now = clock.instant();
            record.setStatus(GenerationJobStatus.CANCELLED);
            record.setErrorCode("CANCELLED_BY_CALLER");
            record.setErrorMessage("The generation job was cancelled by request.");
            record.setCompletedAt(now);
            record.setUpdatedAt(now);
            record.setLeaseOwner(null);
            record.setLeaseUntil(null);
            try {
                GenerationJobRecord saved = eventPublisher.commitTerminal(repository, record);
                Future<?> future = futures.remove(jobId);
                if (future != null) future.cancel(true);
                enqueueTerminalEvent(saved);
                return get(jobId);
            } catch (GenerationJobConflictException concurrentChange) {
                // Worker heartbeat or another caller won the optimistic update; reload and retry.
            }
        }
        throw new GenerationJobConflictException("generation job changed too frequently to cancel");
    }

    private void recoverTerminalEvents() {
        for (GenerationJobRecord record : repository.list(Integer.MAX_VALUE)) {
            if (record.getStatus().isTerminal() && record.getTerminalEventId() == null) {
                enqueueTerminalEvent(record);
            }
        }
    }

    private String encodeCursor(GenerationJobRecord value) {
        String raw = value.getCreatedAt().toString() + "\n" + value.getJobId();
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private Cursor decodeCursor(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            String raw = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
            int separator = raw.indexOf('\n');
            if (separator < 1 || separator != raw.lastIndexOf('\n')) throw new IllegalArgumentException();
            Instant createdAt = Instant.parse(raw.substring(0, separator));
            String jobId = UUID.fromString(raw.substring(separator + 1)).toString();
            return new Cursor(createdAt, jobId);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("generation job cursor is invalid");
        }
    }

    private static final class Cursor {
        private final Instant createdAt;
        private final String jobId;

        private Cursor(Instant createdAt, String jobId) {
            this.createdAt = createdAt;
            this.jobId = jobId;
        }
    }

    private void pollSafely() {
        if (closed) return;
        try {
            for (int count = 0; count < 100 && !closed; count++) {
                Instant now = clock.instant();
                Optional<GenerationJobRecord> exhausted = repository.claimExhausted(
                        workerId, now, now.plus(leaseDuration));
                if (exhausted.isEmpty()) break;
                failExhausted(exhausted.get());
            }
            while (!closed && futures.size() < maxInFlight) {
                Instant now = clock.instant();
                Optional<GenerationJobRecord> claimed = repository.claimNext(
                        workerId, now, now.plus(leaseDuration));
                if (claimed.isEmpty()) return;
                String jobId = claimed.get().getJobId();
                FutureTask<Void> task = new FutureTask<>(() -> {
                    executeClaimed(jobId);
                    return null;
                });
                if (futures.putIfAbsent(jobId, task) == null) {
                    try {
                        executor.execute(task);
                    } catch (RuntimeException rejected) {
                        futures.remove(jobId, task);
                        return;
                    }
                }
            }
        } catch (RuntimeException ignored) {
            // A transient repository failure must not terminate the scheduled poller.
        }
    }

    private void failExhausted(GenerationJobRecord record) {
        try {
            Instant now = clock.instant();
            record.setStatus(GenerationJobStatus.FAILED);
            record.setErrorCode("GENERATION_ATTEMPTS_EXHAUSTED");
            record.setErrorMessage("The previous worker lease expired after the final generation attempt.");
            record.setCompletedAt(now);
            record.setUpdatedAt(now);
            enqueueTerminalEvent(eventPublisher.commitClaimedTerminal(repository, record, workerId));
        } catch (GenerationJobConflictException lostOwnership) {
            // Cancellation or another recovery worker is authoritative.
        }
    }

    private void executeClaimed(String jobId) {
        Thread executionThread = Thread.currentThread();
        AtomicBoolean leaseLost = new AtomicBoolean(false);
        long heartbeatMillis = Math.max(100L, leaseDuration.toMillis() / 3L);
        ScheduledFuture<?> heartbeat = coordinator.scheduleAtFixedRate(() -> {
            try {
                Instant now = clock.instant();
                if (!repository.renewLease(jobId, workerId, now, now.plus(leaseDuration))) {
                    leaseLost.set(true);
                    executionThread.interrupt();
                }
            } catch (RuntimeException transientFailure) {
                // The existing lease remains valid; a later heartbeat can still renew it.
            }
        }, heartbeatMillis, heartbeatMillis, TimeUnit.MILLISECONDS);
        try {
            GenerationJobRecord record = requireOwnedRunning(jobId);
            ExternalToolResult result = executeTool(record);
            if (leaseLost.get()) return;
            heartbeat.cancel(false);
            completeOwned(jobId, result);
        } catch (GenerationJobConflictException lostOwnership) {
            // Cancellation or another worker's recovery is authoritative.
        } catch (RuntimeException e) {
            if (!closed && !leaseLost.get()) failOrRetryOwned(jobId, e);
        } finally {
            heartbeat.cancel(false);
            futures.remove(jobId);
        }
    }

    private void completeOwned(String jobId, ExternalToolResult result) {
        List<GenerationArtifact> artifacts = new ArrayList<>();
        result.getArtifacts().forEach(item -> artifacts.add(GenerationArtifact.from(item)));
        for (int attempt = 0; attempt < 10; attempt++) {
            GenerationJobRecord latest = requireOwnedRunning(jobId);
            latest.setArtifacts(artifacts);
            latest.setStatus(GenerationJobStatus.SUCCEEDED);
            latest.setCompletedAt(clock.instant());
            latest.setUpdatedAt(clock.instant());
            latest.setErrorCode(null);
            latest.setErrorMessage(null);
            try {
                enqueueTerminalEvent(eventPublisher.commitClaimedTerminal(repository, latest, workerId));
                return;
            } catch (GenerationJobConflictException heartbeatRace) {
                // Reload the latest version while the same worker still owns the lease.
            }
        }
        throw new GenerationJobConflictException("generation job changed too frequently to complete");
    }

    private void failOrRetryOwned(String jobId, RuntimeException error) {
        try {
            GenerationJobRecord failed = requireOwnedRunning(jobId);
            boolean retry = !closed && failed.getAttemptCount() < failed.getMaxAttempts();
            failed.setStatus(retry ? GenerationJobStatus.QUEUED : GenerationJobStatus.FAILED);
            failed.setErrorCode(retry ? "GENERATION_RETRY_SCHEDULED" : "GENERATION_FAILED");
            failed.setErrorMessage(safeMessage(error));
            if (!retry) failed.setCompletedAt(clock.instant());
            failed.setUpdatedAt(clock.instant());
            GenerationJobRecord saved = retry ? repository.saveClaimed(failed, workerId)
                    : eventPublisher.commitClaimedTerminal(repository, failed, workerId);
            if (!retry) enqueueTerminalEvent(saved);
        } catch (GenerationJobConflictException | IllegalArgumentException lostOwnership) {
            // Cancellation or another worker's recovery is authoritative.
        }
    }

    private GenerationJobRecord requireOwnedRunning(String jobId) {
        GenerationJobRecord value = requireRecord(jobId);
        if (value.getStatus() != GenerationJobStatus.RUNNING
                || !workerId.equals(value.getLeaseOwner())) {
            throw new GenerationJobConflictException("generation job lease is no longer owned by this worker");
        }
        return value;
    }

    private ExternalToolResult executeTool(GenerationJobRecord record) {
        ObjectNode request = (ObjectNode) record.getRequest();
        if (record.getMode() == GenerationMode.DOCUMENT_SPEC) {
            ObjectNode arguments = ((ObjectNode) request.path("documentSpec")).deepCopy();
            arguments.put("outputFormat", request.path("outputFormat").asText());
            return tools.call(ExternalDocumentToolApplication.EXPORT_DOCUMENT, arguments);
        }
        ObjectNode arguments = mapper.createObjectNode();
        arguments.put("templateId", request.path("templateId").asText());
        arguments.put("version", request.path("templateVersion").asText());
        arguments.set("data", request.path("data").deepCopy());
        arguments.put("outputFormat", request.path("outputFormat").asText());
        return tools.call(ExternalDocumentToolApplication.EXPORT_TEMPLATE, arguments);
    }

    private ObjectNode normalizeRequest(JsonNode request) {
        if (request == null || !request.isObject()) {
            throw new IllegalArgumentException("generation request must be a JSON object");
        }
        ObjectNode value = (ObjectNode) request.deepCopy();
        final GenerationMode mode;
        try {
            mode = GenerationMode.valueOf(requiredText(value, "mode"));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("mode must be TEMPLATE_DATA or DOCUMENT_SPEC", e);
        }
        String outputFormat = requiredText(value, "outputFormat");
        if (!outputFormat.equals("DOCX") && !outputFormat.equals("PDF") && !outputFormat.equals("HTML")) {
            throw new IllegalArgumentException("outputFormat must be DOCX, PDF or HTML");
        }
        if (mode == GenerationMode.DOCUMENT_SPEC) {
            if (!value.path("documentSpec").isObject()) {
                throw new IllegalArgumentException("DOCUMENT_SPEC mode requires documentSpec object");
            }
            validateMaxAttempts(value);
            validateWebhook(value);
            rejectUnexpected(value, "mode", "outputFormat", "documentSpec", "maxAttempts", "webhookId");
        } else {
            requiredText(value, "templateId");
            requiredText(value, "templateVersion");
            if (!value.path("data").isObject()) {
                throw new IllegalArgumentException("TEMPLATE_DATA mode requires data object");
            }
            validateMaxAttempts(value);
            validateWebhook(value);
            rejectUnexpected(value, "mode", "outputFormat", "templateId", "templateVersion", "data",
                    "maxAttempts", "webhookId");
        }
        return value;
    }

    private void validateExecutableRequest(ObjectNode value) {
        String webhookId = value.has("webhookId") ? value.path("webhookId").asText() : null;
        eventPublisher.validateWebhook(tenantId, webhookId);
        GenerationMode mode = GenerationMode.valueOf(value.path("mode").asText());
        if (mode == GenerationMode.DOCUMENT_SPEC) {
            tools.validateDocument(value.path("documentSpec"));
        } else {
            tools.validateTemplateData(value.path("templateId").asText(),
                    value.path("templateVersion").asText(), value.path("data"));
        }
    }

    private void validateMaxAttempts(ObjectNode value) {
        if (!value.has("maxAttempts")) {
            value.put("maxAttempts", 2);
            return;
        }
        JsonNode attempts = value.path("maxAttempts");
        if (!attempts.isIntegralNumber() || attempts.asInt() < 1 || attempts.asInt() > 3) {
            throw new IllegalArgumentException("maxAttempts must be an integer between 1 and 3");
        }
    }

    private void validateWebhook(ObjectNode value) {
        if (!value.has("webhookId")) {
            return;
        }
        JsonNode item = value.path("webhookId");
        if (!item.isTextual() || !item.asText().matches("[A-Za-z0-9._-]{1,64}")) {
            throw new IllegalArgumentException("webhookId is invalid");
        }
    }

    private void enqueueTerminalEvent(GenerationJobRecord record) {
        if (record == null || record.getTerminalEventId() != null
                || record.getRequest() == null || !record.getRequest().has("webhookId")) return;
        try {
            String eventId = eventPublisher.enqueueTerminal(record);
            if (eventId == null) return;
            GenerationJobRecord latest = requireRecord(record.getJobId());
            if (latest.getTerminalEventId() == null) {
                latest.setTerminalEventId(eventId);
                latest.setTerminalEventQueuedAt(clock.instant());
                latest.setUpdatedAt(clock.instant());
                repository.save(latest);
            }
        } catch (RuntimeException ignored) {
            // 任务终态不能被通知后端改写；启动恢复会再次按任务终态键幂等入队。
        }
    }

    private GenerationJobRecord requireRecord(String jobId) {
        return repository.find(jobId)
                .orElseThrow(() -> new IllegalArgumentException("generation job does not exist"));
    }

    private void rejectUnexpected(ObjectNode value, String... allowed) {
        java.util.Set<String> names = new java.util.HashSet<>(java.util.Arrays.asList(allowed));
        value.fieldNames().forEachRemaining(field -> {
            if (!names.contains(field)) throw new IllegalArgumentException("unexpected generation request field: " + field);
        });
    }

    private String requiredText(JsonNode value, String field) {
        JsonNode item = value.path(field);
        if (!item.isTextual() || item.asText().trim().isEmpty()) {
            throw new IllegalArgumentException("generation request requires non-blank field: " + field);
        }
        return item.asText().trim();
    }

    private String normalizeIdempotencyKey(String value) {
        if (value == null || value.isBlank()) return null;
        String key = value.trim();
        if (!key.matches("[A-Za-z0-9._:-]{1,128}")) {
            throw new IllegalArgumentException("Idempotency-Key is invalid");
        }
        return key;
    }

    private String normalizeCorrelationId(String value) {
        if (value == null || value.isBlank()) return UUID.randomUUID().toString();
        String id = value.trim();
        if (!id.matches("[A-Za-z0-9._:-]{1,128}")) {
            throw new IllegalArgumentException("X-Correlation-Id is invalid");
        }
        return id;
    }

    private void requirePrincipal(String principalId) {
        if (principalId == null || !principalId.matches("[A-Za-z0-9._-]{1,64}")) {
            throw new IllegalArgumentException("generation principal id is invalid");
        }
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(64);
            for (byte item : digest) result.append(String.format("%02x", item & 0xff));
            return result.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private String canonicalJson(JsonNode value) {
        try {
            return mapper.writeValueAsString(canonicalNode(value));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("failed to canonicalize generation request", e);
        }
    }

    private JsonNode canonicalNode(JsonNode value) {
        if (value.isObject()) {
            ObjectNode result = mapper.createObjectNode();
            java.util.List<String> names = new java.util.ArrayList<>();
            value.fieldNames().forEachRemaining(names::add);
            java.util.Collections.sort(names);
            names.forEach(name -> result.set(name, canonicalNode(value.get(name))));
            return result;
        }
        if (value.isArray()) {
            ArrayNode result = mapper.createArrayNode();
            value.forEach(item -> result.add(canonicalNode(item)));
            return result;
        }
        return value.deepCopy();
    }

    private String safeMessage(RuntimeException error) {
        String value = error.getMessage();
        if (value == null || value.isBlank()) value = error.getClass().getSimpleName();
        return value.length() > 1000 ? value.substring(0, 1000) : value;
    }

    /**
     * 停止协调器和自有执行线程。
     *
     * <p>正在运行的持久化任务保留租约，其他实例会在租约到期后恢复。</p>
     */
    @Override
    public void close() {
        closed = true;
        coordinator.shutdownNow();
        futures.values().forEach(future -> future.cancel(true));
        futures.clear();
        if (ownsExecutor) executor.shutdownNow();
    }
}
