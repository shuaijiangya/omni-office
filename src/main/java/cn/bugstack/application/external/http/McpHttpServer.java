package cn.bugstack.application.external.http;

import cn.bugstack.application.external.ExternalDocumentToolApplication;
import cn.bugstack.application.external.ResolvedExternalArtifact;
import cn.bugstack.application.external.ExternalArtifactStoreProvider;
import cn.bugstack.application.external.LocalExternalArtifactStoreProvider;
import cn.bugstack.application.audit.AuditEvent;
import cn.bugstack.application.audit.AuditLog;
import cn.bugstack.application.external.mcp.McpJsonRpcServer;
import cn.bugstack.application.external.security.AuthenticationException;
import cn.bugstack.application.external.security.HttpAuthenticationRequest;
import cn.bugstack.application.external.security.HttpAuthenticator;
import cn.bugstack.application.external.security.RequestIdentity;
import cn.bugstack.application.generation.GenerationArtifact;
import cn.bugstack.application.generation.GenerationJobApplication;
import cn.bugstack.application.generation.GenerationJobConflictException;
import cn.bugstack.application.generation.GenerationJobRecord;
import cn.bugstack.application.generation.GenerationJobStatus;
import cn.bugstack.application.generation.GenerationJobPage;
import cn.bugstack.application.generation.FileGenerationJobRepositoryProvider;
import cn.bugstack.application.generation.GenerationJobRepositoryProvider;
import cn.bugstack.application.generation.GenerationQuotaPolicy;
import cn.bugstack.application.generation.GenerationQuotaExceededException;
import cn.bugstack.application.document.DocumentSpecValidationException;
import cn.bugstack.application.document.DocumentSpecViolation;
import cn.bugstack.application.template.DocumentTemplateValidationException;
import cn.bugstack.application.template.DocumentTemplateViolation;
import cn.bugstack.application.template.governance.TemplateLifecycleStatus;
import cn.bugstack.application.template.governance.TemplateManagementApplication;
import cn.bugstack.application.template.governance.TemplateRevision;
import cn.bugstack.application.template.governance.TemplateWorkflowConflictException;
import cn.bugstack.application.schema.SchemaCompatibilityResult;
import cn.bugstack.application.generation.webhook.JsonFileWebhookEndpointRegistry;
import cn.bugstack.application.generation.webhook.WebhookDeliveryRepository;
import cn.bugstack.application.generation.webhook.WebhookDeliveryRecord;
import cn.bugstack.application.generation.webhook.WebhookDispatcher;
import cn.bugstack.application.generation.webhook.WebhookEndpointRegistry;
import cn.bugstack.application.generation.webhook.WebhookOutboxPublisher;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicLong;
import com.sun.net.httpserver.HttpHandler;

/** MCP 2025-11-25 Streamable HTTP（JSON 响应模式）与受控工件下载服务。 */
public final class McpHttpServer implements AutoCloseable {

    private static final String SESSION_HEADER = "MCP-Session-Id";
    private static final String PROTOCOL_HEADER = "MCP-Protocol-Version";
    private final McpHttpServerConfig config;
    private final HttpAuthenticator authenticator;
    private final TenantApplicationRegistry tenants;
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private final HttpServer server;
    private final ExecutorService httpExecutor;
    private final ExecutorService operationExecutor;
    private final ConcurrentMap<String, Session> sessions = new ConcurrentHashMap<>();
    private final IdentityRateLimiter rateLimiter;
    private final Semaphore concurrency;
    private final Clock clock;
    private final AuditLog auditLog;
    private final ScheduledExecutorService maintenanceExecutor;
    private final WebhookDeliveryRepository webhookDeliveries;
    private final WebhookDispatcher webhookDispatcher;
    private final AtomicLong requestsTotal = new AtomicLong();
    private final AtomicLong errorsTotal = new AtomicLong();
    private final AtomicLong downloadsTotal = new AtomicLong();
    private final ConcurrentMap<String, AtomicLong> httpRequestsByRouteStatus = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, AtomicLong> httpDurationNanosByRoute = new ConcurrentHashMap<>();
    private final ThreadLocal<Integer> responseStatus = new ThreadLocal<>();
    private final AtomicLong artifactCleanupRunsTotal = new AtomicLong();
    private final AtomicLong artifactCleanupErrorsTotal = new AtomicLong();
    private final AtomicLong artifactsPurgedTotal = new AtomicLong();
    private final Instant serviceStartedAt;

    /**
     * 创建使用默认无操作审计和本地持久化的 HTTP 服务。
     *
     * @param config 服务配置
     * @param authenticator HTTP 身份认证器
     */
    public McpHttpServer(McpHttpServerConfig config, HttpAuthenticator authenticator) {
        this(config, authenticator, AuditLog.noop());
    }

    /**
     * 创建使用本地任务和工件存储的 HTTP 服务。
     *
     * @param config 服务配置
     * @param authenticator HTTP 身份认证器
     * @param auditLog 审计日志
     */
    public McpHttpServer(McpHttpServerConfig config, HttpAuthenticator authenticator, AuditLog auditLog) {
        this(config, authenticator, auditLog, Clock.systemUTC(),
                new FileGenerationJobRepositoryProvider(config.getDataRoot()),
                new LocalExternalArtifactStoreProvider());
    }

    /**
     * 创建使用指定任务仓储和本地工件存储的 HTTP 服务。
     *
     * @param config 服务配置
     * @param authenticator HTTP 身份认证器
     * @param auditLog 审计日志
     * @param jobRepositories 任务仓储提供器
     */
    public McpHttpServer(McpHttpServerConfig config, HttpAuthenticator authenticator, AuditLog auditLog,
                         GenerationJobRepositoryProvider jobRepositories) {
        this(config, authenticator, auditLog, Clock.systemUTC(), jobRepositories,
                new LocalExternalArtifactStoreProvider());
    }

    /**
     * 创建使用指定任务仓储和工件存储的 HTTP 服务。
     *
     * @param config 服务配置
     * @param authenticator HTTP 身份认证器
     * @param auditLog 审计日志
     * @param jobRepositories 任务仓储提供器
     * @param artifactStores 工件库提供器
     */
    public McpHttpServer(McpHttpServerConfig config, HttpAuthenticator authenticator, AuditLog auditLog,
                         GenerationJobRepositoryProvider jobRepositories,
                         ExternalArtifactStoreProvider artifactStores) {
        this(config, authenticator, auditLog, Clock.systemUTC(), jobRepositories, artifactStores);
    }

    /**
     * 创建完整配置的 HTTP 服务。
     *
     * @param config 服务配置
     * @param authenticator HTTP 身份认证器
     * @param auditLog 审计日志
     * @param jobRepositories 任务仓储提供器
     * @param artifactStores 工件库提供器
     * @param quotaPolicy 租户配额策略
     */
    public McpHttpServer(McpHttpServerConfig config, HttpAuthenticator authenticator, AuditLog auditLog,
                         GenerationJobRepositoryProvider jobRepositories,
                         ExternalArtifactStoreProvider artifactStores,
                         GenerationQuotaPolicy quotaPolicy) {
        this(config, authenticator, auditLog, Clock.systemUTC(), jobRepositories, artifactStores,
                quotaPolicy);
    }

    McpHttpServer(McpHttpServerConfig config, HttpAuthenticator authenticator, Clock clock) {
        this(config, authenticator, AuditLog.noop(), clock,
                new FileGenerationJobRepositoryProvider(config.getDataRoot()),
                new LocalExternalArtifactStoreProvider());
    }

    McpHttpServer(McpHttpServerConfig config, HttpAuthenticator authenticator,
                  AuditLog auditLog, Clock clock) {
        this(config, authenticator, auditLog, clock,
                new FileGenerationJobRepositoryProvider(config.getDataRoot()),
                new LocalExternalArtifactStoreProvider());
    }

    McpHttpServer(McpHttpServerConfig config, HttpAuthenticator authenticator,
                  AuditLog auditLog, Clock clock, GenerationJobRepositoryProvider jobRepositories,
                  ExternalArtifactStoreProvider artifactStores) {
        this(config, authenticator, auditLog, clock, jobRepositories, artifactStores,
                GenerationQuotaPolicy.unlimited());
    }

    McpHttpServer(McpHttpServerConfig config, HttpAuthenticator authenticator,
                  AuditLog auditLog, Clock clock, GenerationJobRepositoryProvider jobRepositories,
                  ExternalArtifactStoreProvider artifactStores, GenerationQuotaPolicy quotaPolicy) {
        if (config == null || authenticator == null || clock == null) {
            throw new IllegalArgumentException("MCP HTTP config, authenticator and clock are required");
        }
        this.config = config;
        this.authenticator = authenticator;
        this.clock = clock;
        this.serviceStartedAt = clock.instant();
        this.auditLog = auditLog == null ? AuditLog.noop() : auditLog;
        if (config.getWebhookConfigPath() == null) {
            this.webhookDeliveries = null;
            this.webhookDispatcher = null;
            this.tenants = new TenantApplicationRegistry(config.getDataRoot(),
                    new cn.bugstack.application.generation.NoopGenerationEventPublisher(), jobRepositories,
                    artifactStores, quotaPolicy);
        } else {
            WebhookEndpointRegistry endpoints = new JsonFileWebhookEndpointRegistry(
                    config.getWebhookConfigPath());
            this.webhookDeliveries = jobRepositories.webhookRepository(
                    config.getDataRoot().resolve("webhook-outbox"));
            WebhookOutboxPublisher publisher = new WebhookOutboxPublisher(endpoints, webhookDeliveries);
            this.webhookDispatcher = new WebhookDispatcher(endpoints, webhookDeliveries);
            this.tenants = new TenantApplicationRegistry(config.getDataRoot(), publisher, jobRepositories,
                    artifactStores, quotaPolicy);
        }
        this.rateLimiter = new IdentityRateLimiter(config.getRequestsPerMinute(), clock);
        this.concurrency = new Semaphore(config.getMaxConcurrentRequests());
        this.httpExecutor = Executors.newFixedThreadPool(config.getMaxConcurrentRequests());
        this.operationExecutor = Executors.newFixedThreadPool(config.getMaxConcurrentRequests());
        this.maintenanceExecutor = Executors.newSingleThreadScheduledExecutor();
        try {
            this.server = HttpServer.create(config.getAddress(), 0);
        } catch (IOException e) {
            tenants.close();
            if (webhookDispatcher != null) webhookDispatcher.close();
            throw new IllegalStateException("failed to bind MCP HTTP server", e);
        }
        server.createContext("/mcp", observed("mcp", this::handleMcp));
        server.createContext("/artifacts", observed("artifacts", this::handleArtifact));
        server.createContext("/v1/generation-jobs", observed("generation_jobs", this::handleGenerationJobs));
        server.createContext("/v1/document-specs/validate", observed("document_validation",
                this::handleDocumentValidation));
        server.createContext("/v1/templates", observed("template_validation", this::handleTemplateValidation));
        server.createContext("/v1/admin/templates", observed("template_management",
                this::handleTemplateManagement));
        server.createContext("/v1/admin/operations/summary", observed("operations_summary",
                this::handleOperationsSummary));
        server.createContext("/v1/webhook-deliveries", observed("webhook_deliveries",
                this::handleWebhookDeliveries));
        server.createContext("/v1/openapi.json", observed("openapi", this::openApi));
        server.createContext("/.well-known/oauth-protected-resource", observed("oauth_metadata",
                this::protectedResourceMetadata));
        server.createContext("/health/live", observed("health_live", exchange -> health(exchange, "UP")));
        server.createContext("/health/ready", observed("health_ready", this::readiness));
        server.createContext("/metrics", observed("metrics", this::metrics));
        server.setExecutor(httpExecutor);
    }

    /** 启动 HTTP、Webhook 投递和工件维护任务。 */
    public void start() {
        if (webhookDispatcher != null) webhookDispatcher.start();
        server.start();
        maintenanceExecutor.scheduleAtFixedRate(() -> {
            artifactCleanupRunsTotal.incrementAndGet();
            try { artifactsPurgedTotal.addAndGet(tenants.purgeExpiredArtifacts(clock.instant())); }
            catch (RuntimeException ignored) { artifactCleanupErrorsTotal.incrementAndGet(); }
        }, 1, 1, TimeUnit.HOURS);
    }

    /** @return 实际绑定地址；端口配置为 0 时可用于获取系统分配端口 */
    public InetSocketAddress getAddress() {
        return server.getAddress();
    }

    /** 停止服务并释放线程池、会话和持久化资源。 */
    @Override
    public void close() {
        server.stop(1);
        sessions.values().forEach(session -> session.server.close());
        sessions.clear();
        tenants.close();
        operationExecutor.shutdownNow();
        httpExecutor.shutdownNow();
        maintenanceExecutor.shutdownNow();
        if (webhookDispatcher != null) webhookDispatcher.close();
    }

    private void handleMcp(HttpExchange exchange) throws IOException {
        requestsTotal.incrementAndGet();
        if (!validOrigin(exchange)) {
            sendText(exchange, 403, "invalid Origin");
            return;
        }
        RequestIdentity identity = authenticate(exchange, "mcp:invoke");
        if (identity == null) {
            return;
        }
        if (!rateLimiter.allow(identity.bindingKey())) {
            exchange.getResponseHeaders().set("Retry-After", "60");
            sendText(exchange, 429, "rate limit exceeded");
            return;
        }
        cleanupExpiredSessions();
        switch (exchange.getRequestMethod()) {
            case "POST":
                handlePost(exchange, identity);
                break;
            case "GET":
                exchange.getResponseHeaders().set("Allow", "POST, DELETE");
                sendText(exchange, 405, "SSE listener is not enabled; use POST JSON responses");
                break;
            case "DELETE":
                deleteSession(exchange, identity);
                break;
            default:
                exchange.getResponseHeaders().set("Allow", "POST, GET, DELETE");
                sendText(exchange, 405, "method not allowed");
        }
    }

    private void handlePost(HttpExchange exchange, RequestIdentity identity) throws IOException {
        if (!acceptsMcp(exchange.getRequestHeaders().getFirst("Accept"))) {
            sendText(exchange, 406, "Accept must include application/json and text/event-stream");
            return;
        }
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null || !contentType.toLowerCase().startsWith("application/json")) {
            sendText(exchange, 415, "Content-Type must be application/json");
            return;
        }
        final JsonNode message;
        try {
            message = mapper.readTree(readBounded(exchange.getRequestBody(), config.getMaxRequestBytes()));
        } catch (PayloadTooLargeException e) {
            sendText(exchange, 413, "request body is too large");
            return;
        } catch (JsonProcessingException e) {
            sendJson(exchange, 400, jsonRpcError(-32700, "Parse error"));
            return;
        }
        boolean initialize = message != null && "initialize".equals(message.path("method").asText())
                && message.has("id");
        Session session;
        if (initialize) {
            if (exchange.getRequestHeaders().getFirst(SESSION_HEADER) != null) {
                sendText(exchange, 400, "initialize must not include MCP-Session-Id");
                return;
            }
            ExternalDocumentToolApplication application = tenants.require(identity.getTenantId());
            session = new Session(UUID.randomUUID().toString(), identity,
                    new McpJsonRpcServer(application, true), clock.instant());
        } else {
            session = requireSession(exchange, identity);
            if (session == null || !validProtocolHeader(exchange, session)) {
                return;
            }
        }
        if (!concurrency.tryAcquire()) {
            sendText(exchange, 503, "server concurrency limit reached");
            return;
        }
        ObjectNode response;
        CompletableFuture<ObjectNode> future = null;
        try {
            future = CompletableFuture.supplyAsync(
                    () -> session.server.handle(message), operationExecutor);
            response = future.get(config.getRequestTimeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            if (future != null) future.cancel(true);
            if (initialize) session.server.close();
            sendText(exchange, 504, "MCP operation timed out");
            return;
        } catch (InterruptedException e) {
            if (future != null) future.cancel(true);
            if (initialize) session.server.close();
            Thread.currentThread().interrupt();
            sendText(exchange, 503, "MCP operation interrupted");
            return;
        } catch (Exception e) {
            if (initialize) session.server.close();
            sendJson(exchange, 500, jsonRpcError(-32603, "Internal server error"));
            return;
        } finally {
            concurrency.release();
        }
        session.touch(clock.instant());
        if (initialize && response != null && !response.has("error")) {
            sessions.put(session.id, session);
            exchange.getResponseHeaders().set(SESSION_HEADER, session.id);
        }
        audit(identity, message.path("method").asText("unknown"),
                response != null && response.has("error") ? "ERROR" : "SUCCESS");
        if (response == null) {
            responseStatus.set(202);
            exchange.sendResponseHeaders(202, -1);
        } else {
            sendJson(exchange, 200, response);
        }
    }

    private void deleteSession(HttpExchange exchange, RequestIdentity identity) throws IOException {
        String id = exchange.getRequestHeaders().getFirst(SESSION_HEADER);
        Session session = id == null ? null : sessions.get(id);
        if (session == null) {
            sendText(exchange, 404, "MCP session not found");
            return;
        }
        if (!session.identity.bindingKey().equals(identity.bindingKey())) {
            sendText(exchange, 403, "MCP session belongs to another identity");
            return;
        }
        sessions.remove(id, session);
        session.server.close();
        responseStatus.set(204);
        exchange.sendResponseHeaders(204, -1);
    }

    private Session requireSession(HttpExchange exchange, RequestIdentity identity) throws IOException {
        String id = exchange.getRequestHeaders().getFirst(SESSION_HEADER);
        if (id == null || id.isEmpty()) {
            sendText(exchange, 400, "MCP-Session-Id is required after initialize");
            return null;
        }
        Session session = sessions.get(id);
        if (session == null) {
            sendText(exchange, 404, "MCP session not found or expired");
            return null;
        }
        if (!session.identity.bindingKey().equals(identity.bindingKey())) {
            sendText(exchange, 403, "MCP session belongs to another identity");
            return null;
        }
        return session;
    }

    private boolean validProtocolHeader(HttpExchange exchange, Session session) throws IOException {
        String actual = exchange.getRequestHeaders().getFirst(PROTOCOL_HEADER);
        if (actual == null || !actual.equals(session.server.getNegotiatedVersion())) {
            sendText(exchange, 400, PROTOCOL_HEADER + " must match negotiated version");
            return false;
        }
        return true;
    }

    private void handleArtifact(HttpExchange exchange) throws IOException {
        requestsTotal.incrementAndGet();
        if (!"GET".equals(exchange.getRequestMethod())) {
            exchange.getResponseHeaders().set("Allow", "GET");
            sendText(exchange, 405, "method not allowed");
            return;
        }
        if (!validOrigin(exchange)) {
            sendText(exchange, 403, "invalid Origin");
            return;
        }
        RequestIdentity identity = authenticate(exchange, "artifacts:read");
        if (identity == null) {
            return;
        }
        String path = exchange.getRequestURI().getPath();
        String prefix = "/artifacts/";
        if (!path.startsWith(prefix) || path.length() <= prefix.length() || path.indexOf('/', prefix.length()) >= 0) {
            sendText(exchange, 404, "artifact not found");
            return;
        }
        String id;
        try {
            id = UUID.fromString(path.substring(prefix.length())).toString();
        } catch (IllegalArgumentException e) {
            sendText(exchange, 404, "artifact not found");
            return;
        }
        try {
            ResolvedExternalArtifact artifact = tenants.require(identity.getTenantId())
                    .readResource("omni-office://artifacts/" + id);
            Headers headers = exchange.getResponseHeaders();
            headers.set("Content-Type", artifact.getReference().getMediaType());
            headers.set("Content-Length", Long.toString(artifact.getReference().getSize()));
            headers.set("Content-Disposition", "attachment; filename=\"" + artifact.getReference().getFileName() + "\"");
            headers.set("X-Checksum-SHA256", artifact.getReference().getSha256());
            headers.set("Cache-Control", "private, no-store");
            responseStatus.set(200);
            exchange.sendResponseHeaders(200, artifact.getReference().getSize());
            try (OutputStream output = exchange.getResponseBody()) {
                Files.copy(artifact.getContentPath(), output);
            }
            audit(identity, "artifact.download", "SUCCESS");
            downloadsTotal.incrementAndGet();
        } catch (RuntimeException e) {
            sendText(exchange, 404, "artifact not found");
        }
    }

    private void handleGenerationJobs(HttpExchange exchange) throws IOException {
        requestsTotal.incrementAndGet();
        if (!validOrigin(exchange)) {
            sendText(exchange, 403, "invalid Origin");
            return;
        }
        String path = exchange.getRequestURI().getPath();
        String base = "/v1/generation-jobs";
        String suffix = path.length() <= base.length() ? "" : path.substring(base.length());
        try {
            if (suffix.isEmpty() || "/".equals(suffix)) {
                handleGenerationCollection(exchange);
                return;
            }
            String[] segments = suffix.substring(1).split("/");
            if (segments.length < 1 || segments.length > 2) {
                sendText(exchange, 404, "generation endpoint not found");
                return;
            }
            String jobId;
            try {
                jobId = UUID.fromString(segments[0]).toString();
            } catch (IllegalArgumentException e) {
                sendText(exchange, 404, "generation job not found");
                return;
            }
            if (segments.length == 1 && "GET".equals(exchange.getRequestMethod())) {
                RequestIdentity identity = authenticate(exchange, "generation:read");
                if (identity == null) return;
                sendJson(exchange, 200, jobNode(requireGeneration(identity).get(jobId)));
                audit(identity, "generation.get", "SUCCESS");
                return;
            }
            if (segments.length == 2 && "cancel".equals(segments[1])
                    && "POST".equals(exchange.getRequestMethod())) {
                RequestIdentity identity = authenticate(exchange, "generation:cancel");
                if (identity == null) return;
                sendJson(exchange, 200, jobNode(requireGeneration(identity).cancel(jobId)));
                audit(identity, "generation.cancel", "SUCCESS");
                return;
            }
            if (segments.length == 2 && "artifacts".equals(segments[1])
                    && "GET".equals(exchange.getRequestMethod())) {
                RequestIdentity identity = authenticate(exchange, "generation:read");
                if (identity == null) return;
                GenerationJobRecord job = requireGeneration(identity).get(jobId);
                ObjectNode result = mapper.createObjectNode();
                result.put("jobId", job.getJobId());
                ArrayNode artifacts = result.putArray("artifacts");
                job.getArtifacts().forEach(item -> artifacts.add(artifactNode(item)));
                sendJson(exchange, 200, result);
                audit(identity, "generation.artifacts", "SUCCESS");
                return;
            }
            exchange.getResponseHeaders().set("Allow", "GET, POST");
            sendText(exchange, 405, "method not allowed");
        } catch (GenerationJobConflictException e) {
            sendProblem(exchange, 409, "GENERATION_JOB_CONFLICT", e.getMessage());
        } catch (GenerationQuotaExceededException e) {
            exchange.getResponseHeaders().set("Retry-After", "60");
            sendProblem(exchange, 429, "GENERATION_QUOTA_EXCEEDED", e.getMessage());
        } catch (DocumentSpecValidationException e) {
            sendDocumentValidationProblem(exchange, e);
        } catch (IllegalArgumentException e) {
            if (e.getMessage() != null && e.getMessage().contains("does not exist")) {
                sendProblem(exchange, 404, "GENERATION_JOB_NOT_FOUND", "generation job not found");
            } else {
                sendProblem(exchange, 400, "INVALID_GENERATION_REQUEST", safeMessage(e));
            }
        } catch (RuntimeException e) {
            sendProblem(exchange, 500, "GENERATION_SERVICE_ERROR", "generation service failed");
        }
    }

    private void handleGenerationCollection(HttpExchange exchange) throws IOException {
        if ("POST".equals(exchange.getRequestMethod())) {
            RequestIdentity identity = authenticate(exchange, "generation:create");
            if (identity == null) return;
            if (!allow(identity, exchange)) return;
            JsonNode request;
            try {
                request = readJsonBody(exchange);
            } catch (PayloadTooLargeException e) {
                sendText(exchange, 413, "request body is too large");
                return;
            } catch (JsonProcessingException e) {
                sendProblem(exchange, 400, "INVALID_JSON", "request body is not valid JSON");
                return;
            }
            if (request == null) return;
            GenerationJobRecord job = requireGeneration(identity).submit(identity.getPrincipalId(),
                    exchange.getRequestHeaders().getFirst("X-Correlation-Id"),
                    exchange.getRequestHeaders().getFirst("Idempotency-Key"), request);
            exchange.getResponseHeaders().set("Location", "/v1/generation-jobs/" + job.getJobId());
            sendJson(exchange, 202, jobNode(job));
            audit(identity, "generation.submit", "SUCCESS");
            return;
        }
        if ("GET".equals(exchange.getRequestMethod())) {
            RequestIdentity identity = authenticate(exchange, "generation:read");
            if (identity == null || !allow(identity, exchange)) return;
            int limit = queryLimit(exchange.getRequestURI().getRawQuery());
            String statusValue = queryValue(exchange.getRequestURI().getRawQuery(), "status");
            GenerationJobStatus status = statusValue == null || statusValue.isBlank() ? null
                    : GenerationJobStatus.valueOf(statusValue);
            String cursor = queryValue(exchange.getRequestURI().getRawQuery(), "cursor");
            GenerationJobPage page = requireGeneration(identity).list(status, cursor, limit);
            ArrayNode jobs = mapper.createArrayNode();
            page.getJobs().forEach(item -> jobs.add(jobNode(item)));
            ObjectNode result = mapper.createObjectNode();
            result.set("jobs", jobs);
            result.put("limit", limit);
            if (page.getNextCursor() != null) result.put("nextCursor", page.getNextCursor());
            sendJson(exchange, 200, result);
            audit(identity, "generation.list", "SUCCESS");
            return;
        }
        exchange.getResponseHeaders().set("Allow", "GET, POST");
        sendText(exchange, 405, "method not allowed");
    }

    private boolean allow(RequestIdentity identity, HttpExchange exchange) throws IOException {
        if (rateLimiter.allow(identity.bindingKey())) return true;
        exchange.getResponseHeaders().set("Retry-After", "60");
        sendText(exchange, 429, "rate limit exceeded");
        return false;
    }

    private void handleDocumentValidation(HttpExchange exchange) throws IOException {
        requestsTotal.incrementAndGet();
        if (!validOrigin(exchange)) {
            sendText(exchange, 403, "invalid Origin");
            return;
        }
        if (!"POST".equals(exchange.getRequestMethod())) {
            exchange.getResponseHeaders().set("Allow", "POST");
            sendText(exchange, 405, "method not allowed");
            return;
        }
        RequestIdentity identity = authenticate(exchange, "generation:create");
        if (identity == null || !allow(identity, exchange)) return;
        try {
            JsonNode document = readJsonBody(exchange);
            if (document == null) return;
            tenants.require(identity.getTenantId()).validateDocument(document);
            ObjectNode result = mapper.createObjectNode();
            result.put("valid", true);
            result.put("schemaVersion", document.path("schemaVersion").asText());
            sendJson(exchange, 200, result);
            audit(identity, "document.validate", "SUCCESS");
        } catch (PayloadTooLargeException e) {
            sendText(exchange, 413, "request body is too large");
        } catch (JsonProcessingException e) {
            sendProblem(exchange, 400, "INVALID_JSON", "request body is not valid JSON");
        } catch (DocumentSpecValidationException e) {
            sendDocumentValidationProblem(exchange, e);
        } catch (IllegalArgumentException e) {
            sendProblem(exchange, 422, "DOCUMENT_SPEC_INVALID", safeMessage(e));
        } catch (RuntimeException e) {
            sendProblem(exchange, 500, "VALIDATION_SERVICE_ERROR", "document validation failed");
        }
    }

    private void handleTemplateValidation(HttpExchange exchange) throws IOException {
        requestsTotal.incrementAndGet();
        if (!validOrigin(exchange)) {
            sendText(exchange, 403, "invalid Origin");
            return;
        }
        String prefix = "/v1/templates/";
        String path = exchange.getRequestURI().getPath();
        if (!path.startsWith(prefix)) {
            sendText(exchange, 404, "template endpoint not found");
            return;
        }
        String[] segments = path.substring(prefix.length()).split("/");
        if (segments.length != 4 || !"versions".equals(segments[1])
                || !"validate-data".equals(segments[3])) {
            sendText(exchange, 404, "template endpoint not found");
            return;
        }
        if (!"POST".equals(exchange.getRequestMethod())) {
            exchange.getResponseHeaders().set("Allow", "POST");
            sendText(exchange, 405, "method not allowed");
            return;
        }
        RequestIdentity identity = authenticate(exchange, "generation:create");
        if (identity == null || !allow(identity, exchange)) return;
        try {
            JsonNode data = readJsonBody(exchange);
            if (data == null) return;
            tenants.require(identity.getTenantId()).validateTemplateData(segments[0], segments[2], data);
            ObjectNode result = mapper.createObjectNode();
            result.put("valid", true);
            result.put("templateId", segments[0]);
            result.put("templateVersion", segments[2]);
            sendJson(exchange, 200, result);
            audit(identity, "template.validate-data", "SUCCESS");
        } catch (PayloadTooLargeException e) {
            sendText(exchange, 413, "request body is too large");
        } catch (JsonProcessingException e) {
            sendProblem(exchange, 400, "INVALID_JSON", "request body is not valid JSON");
        } catch (DocumentTemplateValidationException e) {
            sendTemplateValidationProblem(exchange, e);
        } catch (IllegalArgumentException e) {
            sendProblem(exchange, 422, "TEMPLATE_DATA_INVALID", safeMessage(e));
        } catch (RuntimeException e) {
            sendProblem(exchange, 500, "VALIDATION_SERVICE_ERROR", "template data validation failed");
        }
    }

    private void handleTemplateManagement(HttpExchange exchange) throws IOException {
        requestsTotal.incrementAndGet();
        if (!validOrigin(exchange)) {
            sendText(exchange, 403, "invalid Origin");
            return;
        }
        String base = "/v1/admin/templates";
        String path = exchange.getRequestURI().getPath();
        String suffix = path.length() <= base.length() ? "" : path.substring(base.length());
        try {
            if (suffix.isEmpty() || "/".equals(suffix)) {
                handleTemplateManagementCollection(exchange);
                return;
            }
            String[] segments = suffix.substring(1).split("/");
            if (segments.length == 2 && "compare".equals(segments[1])
                    && "GET".equals(exchange.getRequestMethod())) {
                RequestIdentity identity = authenticate(exchange, "templates:read");
                if (identity == null || !allow(identity, exchange)) return;
                String from = requiredQuery(exchange, "fromVersion");
                String to = requiredQuery(exchange, "toVersion");
                SchemaCompatibilityResult comparison = templateManagement(identity)
                        .compare(segments[0], from, to);
                ObjectNode result = mapper.createObjectNode();
                result.put("templateId", segments[0]);
                result.put("fromVersion", from);
                result.put("toVersion", to);
                result.put("backwardCompatible", comparison.isCompatible());
                ArrayNode violations = result.putArray("violations");
                comparison.getViolations().forEach(violations::add);
                sendJson(exchange, 200, result);
                audit(identity, "template.compare", "SUCCESS");
                return;
            }
            if (segments.length >= 3 && "versions".equals(segments[1])) {
                String templateId = segments[0];
                String version = segments[2];
                if (segments.length == 3 && "GET".equals(exchange.getRequestMethod())) {
                    RequestIdentity identity = authenticate(exchange, "templates:read");
                    if (identity == null || !allow(identity, exchange)) return;
                    sendJson(exchange, 200, templateRevisionNode(
                            templateManagement(identity).get(templateId, version)));
                    audit(identity, "template.get", "SUCCESS");
                    return;
                }
                if (segments.length == 4 && "POST".equals(exchange.getRequestMethod())) {
                    handleTemplateAction(exchange, templateId, version, segments[3]);
                    return;
                }
            }
            sendText(exchange, 404, "template management endpoint not found");
        } catch (PayloadTooLargeException e) {
            sendText(exchange, 413, "request body is too large");
        } catch (JsonProcessingException e) {
            sendProblem(exchange, 400, "INVALID_JSON", "request body is not valid JSON");
        } catch (DocumentTemplateValidationException e) {
            sendTemplateValidationProblem(exchange, e);
        } catch (TemplateWorkflowConflictException e) {
            sendProblem(exchange, 409, "TEMPLATE_STATE_CONFLICT", safeMessage(e));
        } catch (IllegalArgumentException e) {
            if (e.getMessage() != null && e.getMessage().contains("does not exist")) {
                sendProblem(exchange, 404, "TEMPLATE_NOT_FOUND", "template version not found");
            } else {
                sendProblem(exchange, 400, "INVALID_TEMPLATE_REQUEST", safeMessage(e));
            }
        } catch (RuntimeException e) {
            sendProblem(exchange, 500, "TEMPLATE_MANAGEMENT_ERROR", "template management failed");
        }
    }

    private void handleTemplateManagementCollection(HttpExchange exchange)
            throws IOException, PayloadTooLargeException, JsonProcessingException {
        if ("GET".equals(exchange.getRequestMethod())) {
            RequestIdentity identity = authenticate(exchange, "templates:read");
            if (identity == null || !allow(identity, exchange)) return;
            String statusValue = queryValue(exchange.getRequestURI().getRawQuery(), "status");
            TemplateLifecycleStatus status = statusValue == null ? null
                    : TemplateLifecycleStatus.valueOf(statusValue);
            int limit = queryLimit(exchange.getRequestURI().getRawQuery());
            ArrayNode revisions = mapper.createArrayNode();
            templateManagement(identity).list(status, limit)
                    .forEach(item -> revisions.add(templateRevisionNode(item)));
            ObjectNode result = mapper.createObjectNode();
            result.set("templates", revisions);
            result.put("limit", limit);
            sendJson(exchange, 200, result);
            audit(identity, "template.list", "SUCCESS");
            return;
        }
        if ("POST".equals(exchange.getRequestMethod())) {
            RequestIdentity identity = authenticate(exchange, "templates:write");
            if (identity == null || !allow(identity, exchange)) return;
            JsonNode body = readJsonBody(exchange);
            if (body == null) return;
            TemplateRevision created = templateManagement(identity)
                    .createDraft(body, identity.getPrincipalId());
            String templateId = created.getTemplate().getTemplateId();
            String version = created.getTemplate().getVersion();
            exchange.getResponseHeaders().set("Location", "/v1/admin/templates/" + templateId
                    + "/versions/" + version);
            sendJson(exchange, 201, templateRevisionNode(created));
            audit(identity, "template.create", "SUCCESS");
            return;
        }
        exchange.getResponseHeaders().set("Allow", "GET, POST");
        sendText(exchange, 405, "method not allowed");
    }

    private void handleTemplateAction(HttpExchange exchange, String templateId, String version,
                                      String action)
            throws IOException, PayloadTooLargeException, JsonProcessingException {
        String scope = "submit".equals(action) ? "templates:write" : "templates:review";
        RequestIdentity identity = authenticate(exchange, scope);
        if (identity == null || !allow(identity, exchange)) return;
        TemplateManagementApplication management = templateManagement(identity);
        TemplateRevision result;
        if ("submit".equals(action)) {
            result = management.submit(templateId, version, identity.getPrincipalId());
        } else {
            JsonNode body = readJsonBody(exchange);
            if (body == null) return;
            String comment = optionalComment(body);
            switch (action) {
                case "approve":
                    result = management.approve(templateId, version, identity.getPrincipalId(), comment);
                    break;
                case "reject":
                    result = management.reject(templateId, version, identity.getPrincipalId(), comment);
                    break;
                case "retire":
                    result = management.retire(templateId, version, identity.getPrincipalId(), comment);
                    break;
                default:
                    sendText(exchange, 404, "template action not found");
                    return;
            }
        }
        sendJson(exchange, 200, templateRevisionNode(result));
        audit(identity, "template." + action, "SUCCESS");
    }

    private TemplateManagementApplication templateManagement(RequestIdentity identity) {
        return tenants.requireTemplateManagement(identity.getTenantId());
    }

    private ObjectNode templateRevisionNode(TemplateRevision revision) {
        return mapper.valueToTree(revision);
    }

    private String optionalComment(JsonNode body) {
        if (!body.isObject()) throw new IllegalArgumentException("template action body must be an object");
        java.util.Iterator<String> fields = body.fieldNames();
        while (fields.hasNext()) {
            if (!"comment".equals(fields.next())) {
                throw new IllegalArgumentException("template action body contains an unexpected field");
            }
        }
        if (!body.has("comment") || body.path("comment").isNull()) return null;
        if (!body.path("comment").isTextual()) {
            throw new IllegalArgumentException("template action comment must be text");
        }
        return body.path("comment").asText();
    }

    private JsonNode readJsonBody(HttpExchange exchange) throws IOException {
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null || !contentType.toLowerCase().startsWith("application/json")) {
            sendText(exchange, 415, "Content-Type must be application/json");
            return null;
        }
        return mapper.readTree(readBounded(exchange.getRequestBody(), config.getMaxRequestBytes()));
    }

    private void handleWebhookDeliveries(HttpExchange exchange) throws IOException {
        requestsTotal.incrementAndGet();
        if (!"GET".equals(exchange.getRequestMethod())) {
            exchange.getResponseHeaders().set("Allow", "GET");
            sendText(exchange, 405, "method not allowed");
            return;
        }
        if (!validOrigin(exchange)) {
            sendText(exchange, 403, "invalid Origin");
            return;
        }
        RequestIdentity identity = authenticate(exchange, "webhook:read");
        if (identity == null || !allow(identity, exchange)) return;
        if (webhookDeliveries == null) {
            sendProblem(exchange, 404, "WEBHOOK_NOT_CONFIGURED", "webhook delivery is not configured");
            return;
        }
        try {
            int limit = queryLimit(exchange.getRequestURI().getRawQuery());
            ObjectNode result = mapper.createObjectNode();
            ArrayNode deliveries = result.putArray("deliveries");
            webhookDeliveries.list(identity.getTenantId(), limit)
                    .forEach(item -> deliveries.add(webhookDeliveryNode(item)));
            result.put("limit", limit);
            sendJson(exchange, 200, result);
            audit(identity, "webhook.deliveries.list", "SUCCESS");
        } catch (IllegalArgumentException e) {
            sendProblem(exchange, 400, "INVALID_WEBHOOK_QUERY", safeMessage(e));
        }
    }

    private void handleOperationsSummary(HttpExchange exchange) throws IOException {
        requestsTotal.incrementAndGet();
        if (!validOrigin(exchange)) {
            sendText(exchange, 403, "invalid Origin");
            return;
        }
        if (!"GET".equals(exchange.getRequestMethod())) {
            exchange.getResponseHeaders().set("Allow", "GET");
            sendText(exchange, 405, "method not allowed");
            return;
        }
        RequestIdentity identity = authenticate(exchange, "operations:read");
        if (identity == null || !allow(identity, exchange)) return;
        ObjectNode value = mapper.createObjectNode();
        value.put("tenantId", identity.getTenantId());
        value.put("time", clock.instant().toString());
        value.put("startedAt", serviceStartedAt.toString());
        ObjectNode checks = value.putObject("checks");
        tenants.readinessChecks().forEach(checks::put);
        ObjectNode jobs = value.putObject("generationJobs");
        tenants.generationCounts(identity.getTenantId()).forEach((status, count) ->
                jobs.put(status.name(), count));
        ObjectNode webhooks = value.putObject("webhookDeliveries");
        if (webhookDeliveries != null) {
            webhookDeliveries.countsByStatus(identity.getTenantId()).forEach((status, count) ->
                    webhooks.put(status.name(), count));
        }
        sendJson(exchange, 200, value);
        audit(identity, "operations.summary", "SUCCESS");
    }

    private ObjectNode webhookDeliveryNode(WebhookDeliveryRecord record) {
        ObjectNode value = mapper.createObjectNode();
        value.put("eventId", record.getEventId());
        value.put("eventType", record.getEventType());
        value.put("webhookId", record.getWebhookId());
        value.put("jobId", record.getJobId());
        value.put("status", record.getStatus().name());
        value.put("attemptCount", record.getAttemptCount());
        value.put("maxAttempts", record.getMaxAttempts());
        if (record.getResponseStatus() != null) value.put("responseStatus", record.getResponseStatus());
        if (record.getLastError() != null) value.put("lastError", record.getLastError());
        if (record.getNextAttemptAt() != null) value.put("nextAttemptAt", record.getNextAttemptAt().toString());
        value.put("createdAt", record.getCreatedAt().toString());
        value.put("updatedAt", record.getUpdatedAt().toString());
        if (record.getDeliveredAt() != null) value.put("deliveredAt", record.getDeliveredAt().toString());
        return value;
    }

    private GenerationJobApplication requireGeneration(RequestIdentity identity) {
        return tenants.requireGeneration(identity.getTenantId());
    }

    private ObjectNode jobNode(GenerationJobRecord job) {
        ObjectNode value = mapper.createObjectNode();
        value.put("jobId", job.getJobId());
        value.put("tenantId", job.getTenantId());
        value.put("principalId", job.getPrincipalId());
        value.put("correlationId", job.getCorrelationId());
        value.put("mode", job.getMode().name());
        value.put("status", job.getStatus().name());
        value.put("attemptCount", job.getAttemptCount());
        value.put("maxAttempts", job.getMaxAttempts());
        if (job.getRequest() != null && job.getRequest().has("webhookId")) {
            value.put("webhookId", job.getRequest().path("webhookId").asText());
        }
        if (job.getTerminalEventId() != null) value.put("terminalEventId", job.getTerminalEventId());
        if (job.getTerminalEventQueuedAt() != null) {
            value.put("terminalEventQueuedAt", job.getTerminalEventQueuedAt().toString());
        }
        value.put("createdAt", job.getCreatedAt().toString());
        value.put("updatedAt", job.getUpdatedAt().toString());
        if (job.getStartedAt() != null) value.put("startedAt", job.getStartedAt().toString());
        if (job.getCompletedAt() != null) value.put("completedAt", job.getCompletedAt().toString());
        if (job.getErrorCode() != null) value.put("errorCode", job.getErrorCode());
        if (job.getErrorMessage() != null) value.put("errorMessage", job.getErrorMessage());
        ArrayNode artifacts = value.putArray("artifacts");
        job.getArtifacts().forEach(item -> artifacts.add(artifactNode(item)));
        return value;
    }

    private ObjectNode artifactNode(GenerationArtifact artifact) {
        ObjectNode value = mapper.createObjectNode();
        value.put("artifactId", artifact.getArtifactId());
        value.put("resourceUri", artifact.getResourceUri());
        value.put("fileName", artifact.getFileName());
        value.put("mediaType", artifact.getMediaType());
        value.put("size", artifact.getSize());
        value.put("sha256", artifact.getSha256());
        if (artifact.getCreatedAt() != null) value.put("createdAt", artifact.getCreatedAt().toString());
        if (artifact.getExpiresAt() != null) value.put("expiresAt", artifact.getExpiresAt().toString());
        return value;
    }

    private int queryLimit(String query) {
        if (query == null || query.isBlank()) return 20;
        for (String item : query.split("&")) {
            String[] parts = item.split("=", 2);
            if (parts.length == 2 && "limit".equals(parts[0])) {
                try {
                    int value = Integer.parseInt(parts[1]);
                    if (value < 1 || value > 100) throw new IllegalArgumentException("limit must be between 1 and 100");
                    return value;
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("limit must be an integer", e);
                }
            }
        }
        return 20;
    }

    private String requiredQuery(HttpExchange exchange, String name) {
        String value = queryValue(exchange.getRequestURI().getRawQuery(), name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("query parameter is required: " + name);
        }
        return value;
    }

    private String queryValue(String query, String name) {
        if (query == null || query.isBlank()) return null;
        for (String item : query.split("&")) {
            String[] parts = item.split("=", 2);
            String key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
            if (name.equals(key)) {
                return parts.length == 1 ? ""
                        : URLDecoder.decode(parts[1], StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private void sendProblem(HttpExchange exchange, int status, String code, String detail) throws IOException {
        ObjectNode value = mapper.createObjectNode();
        value.put("status", status);
        value.put("code", code);
        value.put("detail", detail);
        sendJson(exchange, status, value);
    }

    private void sendDocumentValidationProblem(HttpExchange exchange,
                                               DocumentSpecValidationException error) throws IOException {
        ObjectNode value = problemNode(422, "DOCUMENT_SPEC_INVALID", "DocumentSpec validation failed");
        ArrayNode violations = value.putArray("violations");
        for (DocumentSpecViolation item : error.getViolations()) {
            violations.addObject().put("path", item.getPath()).put("code", item.getCode())
                    .put("message", item.getMessage());
        }
        sendJson(exchange, 422, value);
    }

    private void sendTemplateValidationProblem(HttpExchange exchange,
                                               DocumentTemplateValidationException error) throws IOException {
        ObjectNode value = problemNode(422, "TEMPLATE_DATA_INVALID", "Template data validation failed");
        ArrayNode violations = value.putArray("violations");
        for (DocumentTemplateViolation item : error.getViolations()) {
            violations.addObject().put("path", item.getPath()).put("code", item.getCode())
                    .put("message", item.getMessage());
        }
        sendJson(exchange, 422, value);
    }

    private ObjectNode problemNode(int status, String code, String detail) {
        ObjectNode value = mapper.createObjectNode();
        value.put("status", status);
        value.put("code", code);
        value.put("detail", detail);
        return value;
    }

    private void openApi(HttpExchange exchange) throws IOException {
        requestsTotal.incrementAndGet();
        if (!"GET".equals(exchange.getRequestMethod())) {
            exchange.getResponseHeaders().set("Allow", "GET");
            sendText(exchange, 405, "method not allowed");
            return;
        }
        try (InputStream input = McpHttpServer.class.getResourceAsStream("/omni-service/1.0/openapi.json")) {
            if (input == null) {
                sendProblem(exchange, 503, "OPENAPI_UNAVAILABLE", "OpenAPI document is unavailable");
                return;
            }
            byte[] content = input.readAllBytes();
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.getResponseHeaders().set("Cache-Control", "public, max-age=300");
            responseStatus.set(200);
            exchange.sendResponseHeaders(200, content.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(content);
            }
        }
    }

    private void protectedResourceMetadata(HttpExchange exchange) throws IOException {
        requestsTotal.incrementAndGet();
        if (!"GET".equals(exchange.getRequestMethod())) {
            exchange.getResponseHeaders().set("Allow", "GET");
            sendText(exchange, 405, "method not allowed");
            return;
        }
        if (config.getAuthorizationServer() == null || config.getResourceIdentifier() == null) {
            sendText(exchange, 404, "OAuth protected resource metadata is not configured");
            return;
        }
        ObjectNode value = mapper.createObjectNode();
        value.put("resource", config.getResourceIdentifier().toString());
        value.putArray("authorization_servers").add(config.getAuthorizationServer().toString());
        value.putArray("scopes_supported").add("mcp:invoke").add("artifacts:read")
                .add("generation:create").add("generation:read").add("generation:cancel")
                .add("webhook:read").add("templates:read").add("templates:write")
                .add("templates:review").add("operations:read");
        sendJson(exchange, 200, value);
    }

    private String safeMessage(RuntimeException error) {
        String value = error.getMessage();
        if (value == null || value.isBlank()) return error.getClass().getSimpleName();
        return value.length() > 1000 ? value.substring(0, 1000) : value;
    }

    private RequestIdentity authenticate(HttpExchange exchange, String scope) throws IOException {
        try {
            RequestIdentity identity = authenticator.authenticate(new HttpAuthenticationRequest(
                    exchange.getRequestHeaders().getFirst("Authorization"),
                    exchange.getRequestHeaders().getFirst("X-API-Key")));
            if (!identity.hasScope(scope)) {
                sendText(exchange, 403, "insufficient scope: " + scope);
                return null;
            }
            return identity;
        } catch (AuthenticationException e) {
            String challenge = "Bearer";
            if (config.getResourceIdentifier() != null) {
                challenge += " resource_metadata=\"" + trimTrailingSlash(
                        config.getResourceIdentifier().toString())
                        + "/.well-known/oauth-protected-resource\"";
            }
            exchange.getResponseHeaders().set("WWW-Authenticate", challenge);
            sendText(exchange, 401, "authentication required");
            return null;
        }
    }

    private boolean validOrigin(HttpExchange exchange) {
        String origin = exchange.getRequestHeaders().getFirst("Origin");
        return origin == null || config.getAllowedOrigins().contains(origin);
    }

    private String trimTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private boolean acceptsMcp(String accept) {
        if (accept == null) {
            return false;
        }
        String lower = accept.toLowerCase();
        return lower.contains("application/json") && lower.contains("text/event-stream");
    }

    private byte[] readBounded(InputStream input, int maximum) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(maximum, 8192));
        byte[] buffer = new byte[8192];
        int total = 0;
        int count;
        while ((count = input.read(buffer)) >= 0) {
            total += count;
            if (total > maximum) {
                throw new PayloadTooLargeException();
            }
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

    private void cleanupExpiredSessions() {
        Instant threshold = clock.instant().minus(config.getSessionTtl());
        for (Map.Entry<String, Session> entry : sessions.entrySet()) {
            if (entry.getValue().lastAccess.isBefore(threshold)) {
                if (sessions.remove(entry.getKey(), entry.getValue())) {
                    entry.getValue().server.close();
                }
            }
        }
    }

    private void health(HttpExchange exchange, String status) throws IOException {
        requestsTotal.incrementAndGet();
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendText(exchange, 405, "method not allowed");
            return;
        }
        ObjectNode value = mapper.createObjectNode();
        value.put("status", status);
        value.put("time", clock.instant().toString());
        sendJson(exchange, 200, value);
    }

    private void readiness(HttpExchange exchange) throws IOException {
        requestsTotal.incrementAndGet();
        Map<String, Boolean> checks = tenants.readinessChecks();
        boolean ready = checks.values().stream().allMatch(Boolean::booleanValue);
        ObjectNode value = mapper.createObjectNode();
        value.put("status", ready ? "READY" : "NOT_READY");
        value.put("time", clock.instant().toString());
        ObjectNode checkValues = value.putObject("checks");
        checks.forEach(checkValues::put);
        sendJson(exchange, ready ? 200 : 503, value);
    }

    private void metrics(HttpExchange exchange) throws IOException {
        requestsTotal.incrementAndGet();
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendText(exchange, 405, "method not allowed");
            return;
        }
        Map<GenerationJobStatus, Long> generationCounts = tenants.generationCounts();
        StringBuilder jobs = new StringBuilder("# TYPE omni_office_generation_jobs gauge\n");
        generationCounts.forEach((status, count) -> jobs.append("omni_office_generation_jobs{status=\"")
                .append(status.name().toLowerCase()).append("\"} ").append(count).append('\n'));
        StringBuilder webhookMetrics = new StringBuilder();
        if (webhookDeliveries != null) {
            webhookMetrics.append("# TYPE omni_office_webhook_deliveries gauge\n");
            webhookDeliveries.countsByStatus().forEach((status, count) -> webhookMetrics
                    .append("omni_office_webhook_deliveries{status=\"")
                    .append(status.name().toLowerCase()).append("\"} ").append(count).append('\n'));
        }
        String value = "# TYPE omni_office_http_requests_total counter\n"
                + "omni_office_http_requests_total " + requestsTotal.get() + "\n"
                + "# TYPE omni_office_http_errors_total counter\n"
                + "omni_office_http_errors_total " + errorsTotal.get() + "\n"
                + "# TYPE omni_office_artifact_downloads_total counter\n"
                + "omni_office_artifact_downloads_total " + downloadsTotal.get() + "\n"
                + "# TYPE omni_office_mcp_sessions gauge\n"
                + "omni_office_mcp_sessions " + sessions.size() + "\n"
                + "# TYPE omni_office_uptime_seconds gauge\n"
                + "omni_office_uptime_seconds "
                + Math.max(0L, Duration.between(serviceStartedAt, clock.instant()).getSeconds()) + "\n"
                + routeMetrics()
                + "# TYPE omni_office_artifact_cleanup_runs_total counter\n"
                + "omni_office_artifact_cleanup_runs_total " + artifactCleanupRunsTotal.get() + "\n"
                + "# TYPE omni_office_artifact_cleanup_errors_total counter\n"
                + "omni_office_artifact_cleanup_errors_total " + artifactCleanupErrorsTotal.get() + "\n"
                + "# TYPE omni_office_artifacts_purged_total counter\n"
                + "omni_office_artifacts_purged_total " + artifactsPurgedTotal.get() + "\n"
                + jobs + webhookMetrics;
        byte[] content = value.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; version=0.0.4; charset=utf-8");
        responseStatus.set(200);
        exchange.sendResponseHeaders(200, content.length);
        try (OutputStream output = exchange.getResponseBody()) { output.write(content); }
    }

    private ObjectNode jsonRpcError(int code, String message) {
        ObjectNode value = mapper.createObjectNode();
        value.put("jsonrpc", "2.0");
        value.set("id", NullNode.getInstance());
        value.putObject("error").put("code", code).put("message", message);
        return value;
    }

    private void sendJson(HttpExchange exchange, int status, JsonNode value) throws IOException {
        if (status >= 400) errorsTotal.incrementAndGet();
        responseStatus.set(status);
        byte[] content = mapper.writeValueAsBytes(value);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, content.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(content);
        }
    }

    private void sendText(HttpExchange exchange, int status, String value) throws IOException {
        if (status >= 400) errorsTotal.incrementAndGet();
        responseStatus.set(status);
        byte[] content = value.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, content.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(content);
        }
    }

    private HttpHandler observed(String route, HttpHandler delegate) {
        return exchange -> {
            long started = System.nanoTime();
            responseStatus.remove();
            try {
                delegate.handle(exchange);
            } finally {
                int status = responseStatus.get() == null ? 500 : responseStatus.get();
                responseStatus.remove();
                String key = route + "\n" + status;
                httpRequestsByRouteStatus.computeIfAbsent(key, ignored -> new AtomicLong())
                        .incrementAndGet();
                httpDurationNanosByRoute.computeIfAbsent(route, ignored -> new AtomicLong())
                        .addAndGet(Math.max(0L, System.nanoTime() - started));
            }
        };
    }

    private String routeMetrics() {
        StringBuilder value = new StringBuilder("# TYPE omni_office_http_route_requests_total counter\n");
        httpRequestsByRouteStatus.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    String[] parts = entry.getKey().split("\\n", 2);
                    value.append("omni_office_http_route_requests_total{route=\"")
                            .append(parts[0]).append("\",status=\"").append(parts[1])
                            .append("\"} ").append(entry.getValue().get()).append('\n');
                });
        value.append("# TYPE omni_office_http_route_duration_seconds summary\n");
        httpDurationNanosByRoute.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    long count = httpRequestsByRouteStatus.entrySet().stream()
                            .filter(item -> item.getKey().startsWith(entry.getKey() + "\n"))
                            .mapToLong(item -> item.getValue().get()).sum();
                    value.append("omni_office_http_route_duration_seconds_sum{route=\"")
                            .append(entry.getKey()).append("\"} ")
                            .append(entry.getValue().get() / 1_000_000_000.0d).append('\n')
                            .append("omni_office_http_route_duration_seconds_count{route=\"")
                            .append(entry.getKey()).append("\"} ").append(count).append('\n');
                });
        return value.toString();
    }

    private void audit(RequestIdentity identity, String action, String outcome) {
        try {
            AuditEvent event = new AuditEvent();
            event.setTime(clock.instant());
            event.setAction(action);
            event.setTenantId(identity.getTenantId());
            event.setPrincipalId(identity.getPrincipalId());
            event.setOutcome(outcome);
            event.setAttributes(Collections.emptyMap());
            auditLog.record(event);
        } catch (RuntimeException ignored) {
            // 审计后端故障不应把已经完成的文档响应改写为失败。
        }
    }

    private static final class Session {
        private final String id;
        private final RequestIdentity identity;
        private final McpJsonRpcServer server;
        private volatile Instant lastAccess;

        private Session(String id, RequestIdentity identity, McpJsonRpcServer server, Instant lastAccess) {
            this.id = id;
            this.identity = identity;
            this.server = server;
            this.lastAccess = lastAccess;
        }

        private void touch(Instant value) {
            lastAccess = value;
        }
    }

    private static final class PayloadTooLargeException extends IOException {
    }
}
