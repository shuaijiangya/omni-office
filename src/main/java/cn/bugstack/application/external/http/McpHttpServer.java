package cn.bugstack.application.external.http;

import cn.bugstack.application.external.ExternalDocumentToolApplication;
import cn.bugstack.application.external.ResolvedExternalArtifact;
import cn.bugstack.application.audit.AuditEvent;
import cn.bugstack.application.audit.AuditLog;
import cn.bugstack.application.external.mcp.McpJsonRpcServer;
import cn.bugstack.application.external.security.AuthenticationException;
import cn.bugstack.application.external.security.HttpAuthenticationRequest;
import cn.bugstack.application.external.security.HttpAuthenticator;
import cn.bugstack.application.external.security.RequestIdentity;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Clock;
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

/** MCP 2025-11-25 Streamable HTTP（JSON 响应模式）与受控工件下载服务。 */
public final class McpHttpServer implements AutoCloseable {

    private static final String SESSION_HEADER = "MCP-Session-Id";
    private static final String PROTOCOL_HEADER = "MCP-Protocol-Version";
    private final McpHttpServerConfig config;
    private final HttpAuthenticator authenticator;
    private final TenantApplicationRegistry tenants;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpServer server;
    private final ExecutorService httpExecutor;
    private final ExecutorService operationExecutor;
    private final ConcurrentMap<String, Session> sessions = new ConcurrentHashMap<>();
    private final IdentityRateLimiter rateLimiter;
    private final Semaphore concurrency;
    private final Clock clock;
    private final AuditLog auditLog;
    private final ScheduledExecutorService maintenanceExecutor;
    private final AtomicLong requestsTotal = new AtomicLong();
    private final AtomicLong errorsTotal = new AtomicLong();
    private final AtomicLong downloadsTotal = new AtomicLong();

    public McpHttpServer(McpHttpServerConfig config, HttpAuthenticator authenticator) {
        this(config, authenticator, AuditLog.noop());
    }

    public McpHttpServer(McpHttpServerConfig config, HttpAuthenticator authenticator, AuditLog auditLog) {
        this(config, authenticator, auditLog, Clock.systemUTC());
    }

    McpHttpServer(McpHttpServerConfig config, HttpAuthenticator authenticator, Clock clock) {
        this(config, authenticator, AuditLog.noop(), clock);
    }

    McpHttpServer(McpHttpServerConfig config, HttpAuthenticator authenticator,
                  AuditLog auditLog, Clock clock) {
        if (config == null || authenticator == null || clock == null) {
            throw new IllegalArgumentException("MCP HTTP config, authenticator and clock are required");
        }
        this.config = config;
        this.authenticator = authenticator;
        this.clock = clock;
        this.auditLog = auditLog == null ? AuditLog.noop() : auditLog;
        this.tenants = new TenantApplicationRegistry(config.getDataRoot());
        this.rateLimiter = new IdentityRateLimiter(config.getRequestsPerMinute(), clock);
        this.concurrency = new Semaphore(config.getMaxConcurrentRequests());
        this.httpExecutor = Executors.newFixedThreadPool(config.getMaxConcurrentRequests());
        this.operationExecutor = Executors.newFixedThreadPool(config.getMaxConcurrentRequests());
        this.maintenanceExecutor = Executors.newSingleThreadScheduledExecutor();
        try {
            this.server = HttpServer.create(config.getAddress(), 0);
        } catch (IOException e) {
            throw new IllegalStateException("failed to bind MCP HTTP server", e);
        }
        server.createContext("/mcp", this::handleMcp);
        server.createContext("/artifacts", this::handleArtifact);
        server.createContext("/health/live", exchange -> health(exchange, "UP"));
        server.createContext("/health/ready", this::readiness);
        server.createContext("/metrics", this::metrics);
        server.setExecutor(httpExecutor);
    }

    public void start() {
        server.start();
        maintenanceExecutor.scheduleAtFixedRate(() -> {
            try { tenants.purgeExpiredArtifacts(clock.instant()); }
            catch (RuntimeException ignored) { }
        }, 1, 1, TimeUnit.HOURS);
    }

    public InetSocketAddress getAddress() {
        return server.getAddress();
    }

    @Override
    public void close() {
        server.stop(1);
        sessions.values().forEach(session -> session.server.close());
        sessions.clear();
        operationExecutor.shutdownNow();
        httpExecutor.shutdownNow();
        maintenanceExecutor.shutdownNow();
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
            exchange.getResponseHeaders().set("WWW-Authenticate", "Bearer");
            sendText(exchange, 401, "authentication required");
            return null;
        }
    }

    private boolean validOrigin(HttpExchange exchange) {
        String origin = exchange.getRequestHeaders().getFirst("Origin");
        return origin == null || config.getAllowedOrigins().contains(origin);
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
        boolean ready = tenants.isReady();
        ObjectNode value = mapper.createObjectNode();
        value.put("status", ready ? "READY" : "NOT_READY");
        value.put("time", clock.instant().toString());
        sendJson(exchange, ready ? 200 : 503, value);
    }

    private void metrics(HttpExchange exchange) throws IOException {
        requestsTotal.incrementAndGet();
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendText(exchange, 405, "method not allowed");
            return;
        }
        String value = "# TYPE omni_office_http_requests_total counter\n"
                + "omni_office_http_requests_total " + requestsTotal.get() + "\n"
                + "# TYPE omni_office_http_errors_total counter\n"
                + "omni_office_http_errors_total " + errorsTotal.get() + "\n"
                + "# TYPE omni_office_artifact_downloads_total counter\n"
                + "omni_office_artifact_downloads_total " + downloadsTotal.get() + "\n"
                + "# TYPE omni_office_mcp_sessions gauge\n"
                + "omni_office_mcp_sessions " + sessions.size() + "\n";
        byte[] content = value.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; version=0.0.4; charset=utf-8");
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
        byte[] content = value.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, content.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(content);
        }
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
