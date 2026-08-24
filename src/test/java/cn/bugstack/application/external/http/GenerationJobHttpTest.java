package cn.bugstack.application.external.http;

import cn.bugstack.application.external.security.RequestIdentity;
import cn.bugstack.application.external.security.StaticApiKeyAuthenticator;
import cn.bugstack.application.audit.AuditLog;
import cn.bugstack.application.external.LocalExternalArtifactStoreProvider;
import cn.bugstack.application.generation.FileGenerationJobRepositoryProvider;
import cn.bugstack.application.generation.GenerationQuota;
import cn.bugstack.application.generation.GenerationJobRecord;
import cn.bugstack.application.generation.GenerationJobRepository;
import cn.bugstack.application.generation.GenerationJobStatus;
import cn.bugstack.application.generation.GenerationMode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GenerationJobHttpTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void submitsListsReadsAndDownloadsTenantIsolatedGenerationJobs() throws Exception {
        Map<String, RequestIdentity> keys = new LinkedHashMap<>();
        keys.put("tenant-a-key", identity("tenant-a", "alice"));
        keys.put("tenant-b-key", identity("tenant-b", "bob"));
        McpHttpServerConfig config = new McpHttpServerConfig(new InetSocketAddress("127.0.0.1", 0),
                Files.createTempDirectory("generation-http"), Collections.emptySet(), 2 * 1024 * 1024,
                500, 4, Duration.ofSeconds(30), Duration.ofMinutes(5));
        try (McpHttpServer server = new McpHttpServer(config, new StaticApiKeyAuthenticator(keys))) {
            server.start();
            URI endpoint = URI.create("http://127.0.0.1:" + server.getAddress().getPort()
                    + "/v1/generation-jobs");

            HttpResponse<byte[]> openApi = get(endpoint.resolve("/v1/openapi.json"), null);
            assertEquals(200, openApi.statusCode());
            assertEquals("3.0.3", mapper.readTree(openApi.body()).path("openapi").asText());

            JsonNode spec = documentRequest().path("documentSpec");
            HttpResponse<byte[]> valid = post(endpoint.resolve("/v1/document-specs/validate"),
                    "tenant-a-key", null, spec);
            assertEquals(200, valid.statusCode(), new String(valid.body()));
            ObjectNode invalidSpec = ((ObjectNode) spec).deepCopy();
            invalidSpec.remove("sections");
            HttpResponse<byte[]> invalid = post(endpoint.resolve("/v1/document-specs/validate"),
                    "tenant-a-key", null, invalidSpec);
            assertEquals(422, invalid.statusCode());
            assertEquals("DOCUMENT_SPEC_INVALID", mapper.readTree(invalid.body()).path("code").asText());

            JsonNode templateData;
            try (InputStream input = getClass().getResourceAsStream(
                    "/document-template/1.0/example-assessment-data.json")) {
                templateData = mapper.readTree(input);
            }
            URI templateValidation = endpoint.resolve(
                    "/v1/templates/system.assessment/versions/1.0.0/validate-data");
            assertEquals(200, post(templateValidation, "tenant-a-key", null, templateData).statusCode());

            assertEquals(401, post(endpoint, null, null, documentRequest()).statusCode());
            ObjectNode unconfiguredWebhook = documentRequest();
            unconfiguredWebhook.put("webhookId", "erp");
            assertEquals(400, post(endpoint, "tenant-a-key", null, unconfiguredWebhook).statusCode());
            HttpResponse<byte[]> submitted = post(endpoint, "tenant-a-key", "request-1", documentRequest());
            assertEquals(202, submitted.statusCode(), new String(submitted.body()));
            JsonNode created = mapper.readTree(submitted.body());
            String jobId = created.path("jobId").asText();
            assertFalse(jobId.isBlank());
            assertTrue(submitted.headers().firstValue("Location").orElse("").endsWith(jobId));

            HttpResponse<byte[]> duplicate = post(endpoint, "tenant-a-key", "request-1", documentRequest());
            assertEquals(202, duplicate.statusCode());
            assertEquals(jobId, mapper.readTree(duplicate.body()).path("jobId").asText());

            URI jobEndpoint = endpoint.resolve("/v1/generation-jobs/" + jobId);
            JsonNode completed = awaitTerminal(jobEndpoint, "tenant-a-key");
            assertEquals("SUCCEEDED", completed.path("status").asText(), completed.toString());
            assertEquals(404, get(jobEndpoint, "tenant-b-key").statusCode());

            ObjectNode templateRequest = mapper.createObjectNode();
            templateRequest.put("mode", "TEMPLATE_DATA");
            templateRequest.put("outputFormat", "HTML");
            templateRequest.put("templateId", "system.assessment");
            templateRequest.put("templateVersion", "1.0.0");
            templateRequest.set("data", templateData);
            JsonNode templateJob = mapper.readTree(post(endpoint, "tenant-a-key", "template-request-1",
                    templateRequest).body());
            JsonNode templateCompleted = awaitTerminal(endpoint.resolve("/v1/generation-jobs/"
                    + templateJob.path("jobId").asText()), "tenant-a-key");
            assertEquals("SUCCEEDED", templateCompleted.path("status").asText(), templateCompleted.toString());

            JsonNode listed = mapper.readTree(get(endpoint.resolve("/v1/generation-jobs?limit=10"),
                    "tenant-a-key").body());
            assertEquals(2, listed.path("jobs").size());
            JsonNode firstPage = mapper.readTree(get(endpoint.resolve(
                    "/v1/generation-jobs?limit=1&status=SUCCEEDED"), "tenant-a-key").body());
            assertEquals(1, firstPage.path("jobs").size());
            assertFalse(firstPage.path("nextCursor").asText().isBlank());
            JsonNode secondPage = mapper.readTree(get(endpoint.resolve("/v1/generation-jobs?limit=1&status=SUCCEEDED&cursor="
                    + java.net.URLEncoder.encode(firstPage.path("nextCursor").asText(),
                    java.nio.charset.StandardCharsets.UTF_8)), "tenant-a-key").body());
            assertEquals(1, secondPage.path("jobs").size());
            assertTrue(secondPage.path("nextCursor").isMissingNode());

            URI artifactsEndpoint = endpoint.resolve("/v1/generation-jobs/" + jobId + "/artifacts");
            JsonNode artifacts = mapper.readTree(get(artifactsEndpoint, "tenant-a-key").body());
            String resourceUri = artifacts.path("artifacts").path(0).path("resourceUri").asText();
            String artifactId = resourceUri.substring(resourceUri.lastIndexOf('/') + 1);
            HttpResponse<byte[]> download = get(endpoint.resolve("/artifacts/" + artifactId), "tenant-a-key");
            assertEquals(200, download.statusCode());
            assertEquals("text/html", download.headers().firstValue("Content-Type").orElseThrow());

            JsonNode operationsA = mapper.readTree(get(endpoint.resolve(
                    "/v1/admin/operations/summary"), "tenant-a-key").body());
            JsonNode operationsB = mapper.readTree(get(endpoint.resolve(
                    "/v1/admin/operations/summary"), "tenant-b-key").body());
            assertEquals(2, operationsA.path("generationJobs").path("SUCCEEDED").asInt());
            assertEquals(0, operationsB.path("generationJobs").path("SUCCEEDED").asInt());
            assertTrue(operationsA.path("checks").path("generationRepository").asBoolean());

            String metrics = new String(get(endpoint.resolve("/metrics"), null).body());
            assertTrue(metrics.contains("omni_office_generation_jobs{status=\"succeeded\"} 2"));
        }
    }

    @Test
    void returns429WhenAdministratorConfiguredDailyQuotaIsExhausted() throws Exception {
        java.nio.file.Path root = Files.createTempDirectory("generation-http-quota");
        Map<String, RequestIdentity> keys = Map.of("tenant-a-key", identity("tenant-a", "alice"));
        McpHttpServerConfig config = new McpHttpServerConfig(new InetSocketAddress("127.0.0.1", 0),
                root, Collections.emptySet(), 2 * 1024 * 1024, 500, 4,
                Duration.ofSeconds(30), Duration.ofMinutes(5));
        try (McpHttpServer server = new McpHttpServer(config, new StaticApiKeyAuthenticator(keys),
                AuditLog.noop(), new FileGenerationJobRepositoryProvider(root),
                new LocalExternalArtifactStoreProvider(), tenant -> new GenerationQuota(10, 1))) {
            server.start();
            URI endpoint = URI.create("http://127.0.0.1:" + server.getAddress().getPort()
                    + "/v1/generation-jobs");
            assertEquals(202, post(endpoint, "tenant-a-key", "quota-1", documentRequest()).statusCode());
            HttpResponse<byte[]> rejected = post(endpoint, "tenant-a-key", "quota-2", documentRequest());
            assertEquals(429, rejected.statusCode());
            assertEquals("GENERATION_QUOTA_EXCEEDED",
                    mapper.readTree(rejected.body()).path("code").asText());
            assertEquals("60", rejected.headers().firstValue("Retry-After").orElseThrow());
        }
    }

    @Test
    void keepsJobsAndArtifactsPrivateToPrincipalUnlessAnyScopeIsGranted() throws Exception {
        Map<String, RequestIdentity> keys = new LinkedHashMap<>();
        keys.put("alice-key", scopedIdentity("tenant-a", "alice", "generation:create",
                "generation:read", "artifacts:read"));
        keys.put("bob-key-1", scopedIdentity("tenant-a", "bob", "generation:read", "artifacts:read"));
        keys.put("admin-key", scopedIdentity("tenant-a", "admin", "generation:read:any",
                "artifacts:read:any"));
        McpHttpServerConfig config = new McpHttpServerConfig(new InetSocketAddress("127.0.0.1", 0),
                Files.createTempDirectory("generation-principal-http"), Collections.emptySet(),
                2 * 1024 * 1024, 500, 4, Duration.ofSeconds(30), Duration.ofMinutes(5));
        try (McpHttpServer server = new McpHttpServer(config, new StaticApiKeyAuthenticator(keys))) {
            server.start();
            URI endpoint = URI.create("http://127.0.0.1:" + server.getAddress().getPort()
                    + "/v1/generation-jobs");
            JsonNode submitted = mapper.readTree(post(endpoint, "alice-key", "alice-owned",
                    documentRequest()).body());
            String jobId = submitted.path("jobId").asText();
            JsonNode completed = awaitTerminal(endpoint.resolve("/v1/generation-jobs/" + jobId), "alice-key");
            String resourceUri = completed.path("artifacts").path(0).path("resourceUri").asText();
            String artifactId = resourceUri.substring(resourceUri.lastIndexOf('/') + 1);

            assertEquals(404, get(endpoint.resolve("/v1/generation-jobs/" + jobId), "bob-key-1").statusCode());
            JsonNode bobJobs = mapper.readTree(get(endpoint, "bob-key-1").body());
            assertEquals(0, bobJobs.path("jobs").size());
            assertEquals(404, get(endpoint.resolve("/artifacts/" + artifactId), "bob-key-1").statusCode());

            assertEquals(200, get(endpoint.resolve("/v1/generation-jobs/" + jobId), "admin-key").statusCode());
            assertEquals(200, get(endpoint.resolve("/artifacts/" + artifactId), "admin-key").statusCode());
        }
    }

    @Test
    void publishesTerminalEventThroughPreRegisteredWebhook() throws Exception {
        com.sun.net.httpserver.HttpServer receiver = com.sun.net.httpserver.HttpServer.create(
                new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<String> receivedEvent = new AtomicReference<>();
        receiver.createContext("/events", exchange -> {
            receivedEvent.set(new String(exchange.getRequestBody().readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        receiver.start();
        try {
            java.nio.file.Path dataRoot = Files.createTempDirectory("generation-webhook-http");
            java.nio.file.Path webhookConfig = Files.createTempFile("generation-webhooks", ".json")
                    .toAbsolutePath();
            String receiverUrl = "http://127.0.0.1:" + receiver.getAddress().getPort() + "/events";
            ObjectNode root = mapper.createObjectNode();
            root.putObject("tenants").putObject("tenant-a").putObject("erp")
                    .put("url", receiverUrl)
                    .put("secret", "0123456789abcdef0123456789abcdef");
            Files.writeString(webhookConfig, mapper.writeValueAsString(root));
            Map<String, RequestIdentity> keys = new LinkedHashMap<>();
            keys.put("tenant-a-key", identity("tenant-a", "alice"));
            McpHttpServerConfig config = new McpHttpServerConfig(new InetSocketAddress("127.0.0.1", 0),
                    dataRoot, Collections.emptySet(), 2 * 1024 * 1024, 500, 4,
                    Duration.ofSeconds(30), Duration.ofMinutes(5), webhookConfig);
            try (McpHttpServer server = new McpHttpServer(config, new StaticApiKeyAuthenticator(keys))) {
                server.start();
                URI endpoint = URI.create("http://127.0.0.1:" + server.getAddress().getPort()
                        + "/v1/generation-jobs");
                ObjectNode request = documentRequest();
                request.put("webhookId", "erp");
                JsonNode submitted = mapper.readTree(post(endpoint, "tenant-a-key", "with-webhook", request).body());
                JsonNode completed = awaitTerminal(endpoint.resolve("/v1/generation-jobs/"
                        + submitted.path("jobId").asText()), "tenant-a-key");
                assertEquals("SUCCEEDED", completed.path("status").asText());
                assertFalse(completed.path("terminalEventId").asText().isBlank());

                Instant deadline = Instant.now().plusSeconds(5);
                while (receivedEvent.get() == null && Instant.now().isBefore(deadline)) Thread.sleep(20);
                assertTrue(receivedEvent.get() != null, "webhook event was not delivered");
                JsonNode event = mapper.readTree(receivedEvent.get());
                assertEquals(completed.path("jobId").asText(), event.path("data").path("jobId").asText());
                assertFalse(receivedEvent.get().contains("documentSpec"));
                JsonNode deliveries;
                do {
                    deliveries = mapper.readTree(get(endpoint.resolve("/v1/webhook-deliveries"),
                            "tenant-a-key").body());
                    if ("DELIVERED".equals(deliveries.path("deliveries").path(0).path("status").asText())) break;
                    Thread.sleep(20);
                } while (Instant.now().isBefore(deadline));
                assertEquals(1, deliveries.path("deliveries").size());
                assertEquals("DELIVERED", deliveries.path("deliveries").path(0).path("status").asText());
            }
        } finally {
            receiver.stop(0);
        }
    }

    @Test
    void recoversPersistedTenantWorkerOnServerStartWithoutNewTenantRequest() throws Exception {
        java.nio.file.Path root = Files.createTempDirectory("generation-cold-recovery");
        FileGenerationJobRepositoryProvider repositories = new FileGenerationJobRepositoryProvider(root);
        GenerationJobRepository repository = repositories.repository("tenant-a");
        Instant now = Instant.now();
        GenerationJobRecord queued = new GenerationJobRecord();
        queued.setJobId(UUID.randomUUID().toString());
        queued.setTenantId("tenant-a");
        queued.setPrincipalId("alice");
        queued.setCorrelationId("cold-recovery");
        queued.setRequestSha256("cold-recovery-request");
        queued.setMode(GenerationMode.DOCUMENT_SPEC);
        queued.setRequest(documentRequest());
        queued.setStatus(GenerationJobStatus.QUEUED);
        queued.setMaxAttempts(2);
        queued.setCreatedAt(now);
        queued.setUpdatedAt(now);
        repository.create(queued);

        Map<String, RequestIdentity> keys = Map.of("tenant-a-key", identity("tenant-a", "alice"));
        McpHttpServerConfig config = new McpHttpServerConfig(new InetSocketAddress("127.0.0.1", 0),
                root, Collections.emptySet(), 2 * 1024 * 1024, 500, 4,
                Duration.ofSeconds(30), Duration.ofMinutes(5));
        try (McpHttpServer server = new McpHttpServer(config, new StaticApiKeyAuthenticator(keys),
                AuditLog.noop(), repositories, new LocalExternalArtifactStoreProvider())) {
            server.start();
            Instant deadline = Instant.now().plusSeconds(20);
            GenerationJobRecord recovered;
            do {
                recovered = repository.find(queued.getJobId()).orElseThrow();
                if (recovered.getStatus().isTerminal()) break;
                Thread.sleep(20);
            } while (Instant.now().isBefore(deadline));
            assertEquals(GenerationJobStatus.SUCCEEDED, recovered.getStatus());
        }
    }

    @Test
    void requiresAiScopeAndConfiguredProviderBeforeAcceptingAiJob() throws Exception {
        java.nio.file.Path root = Files.createTempDirectory("generation-ai-http-guard");
        Map<String, RequestIdentity> keys = new LinkedHashMap<>();
        keys.put("without-ai-key", scopedIdentity("tenant-a", "alice", "generation:create"));
        keys.put("internal-ai-key", scopedIdentity("tenant-a", "alice", "generation:create", "ai:generate"));
        McpHttpServerConfig config = new McpHttpServerConfig(new InetSocketAddress("127.0.0.1", 0),
                root, Collections.emptySet(), 2 * 1024 * 1024, 500, 4,
                Duration.ofSeconds(30), Duration.ofMinutes(5));
        ObjectNode request = aiRequest();
        try (McpHttpServer server = new McpHttpServer(config, new StaticApiKeyAuthenticator(keys))) {
            server.start();
            URI endpoint = URI.create("http://127.0.0.1:" + server.getAddress().getPort()
                    + "/v1/generation-jobs");
            assertEquals(403, post(endpoint, "without-ai-key", null, request).statusCode());
            HttpResponse<byte[]> unavailable = post(endpoint, "internal-ai-key", null, request);
            assertEquals(503, unavailable.statusCode());
            assertEquals("AI_GENERATION_NOT_CONFIGURED",
                    mapper.readTree(unavailable.body()).path("code").asText());
        }
    }

    @Test
    void runsConfiguredStructuredAiAsPrincipalOwnedGenerationJob() throws Exception {
        java.nio.file.Path root = Files.createTempDirectory("generation-ai-http");
        String modelOutput;
        try (InputStream input = getClass().getResourceAsStream("/document-spec/1.0/example-simple.json")) {
            modelOutput = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        Map<String, RequestIdentity> keys = Map.of("internal-ai-key", scopedIdentity("tenant-a", "alice",
                "generation:create", "generation:read", "artifacts:read", "ai:generate"));
        McpHttpServerConfig config = new McpHttpServerConfig(new InetSocketAddress("127.0.0.1", 0),
                root, Collections.emptySet(), 2 * 1024 * 1024, 500, 4,
                Duration.ofSeconds(30), Duration.ofMinutes(5));
        try (McpHttpServer server = new McpHttpServer(config, new StaticApiKeyAuthenticator(keys),
                AuditLog.noop(), new FileGenerationJobRepositoryProvider(root),
                new LocalExternalArtifactStoreProvider(),
                cn.bugstack.application.generation.GenerationQuotaPolicy.unlimited(),
                Duration.ofDays(30), Duration.ofDays(30), ignored -> modelOutput)) {
            server.start();
            URI endpoint = URI.create("http://127.0.0.1:" + server.getAddress().getPort()
                    + "/v1/generation-jobs");
            JsonNode submitted = mapper.readTree(post(endpoint, "internal-ai-key", "ai-http", aiRequest()).body());
            JsonNode completed = awaitTerminal(endpoint.resolve("/v1/generation-jobs/"
                    + submitted.path("jobId").asText()), "internal-ai-key");
            assertEquals("SUCCEEDED", completed.path("status").asText(), completed.toString());
            assertEquals("AI_FREEFORM", completed.path("mode").asText());
            assertEquals(1, completed.path("maxAttempts").asInt());
            assertEquals("text/html", completed.path("artifacts").path(0).path("mediaType").asText());
        }
    }

    private RequestIdentity identity(String tenant, String principal) {
        return new RequestIdentity(tenant, principal, Collections.singleton("*"));
    }

    private RequestIdentity scopedIdentity(String tenant, String principal, String... scopes) {
        return new RequestIdentity(tenant, principal,
                new java.util.LinkedHashSet<>(java.util.Arrays.asList(scopes)));
    }

    private HttpResponse<byte[]> post(URI uri, String key, String idempotencyKey, JsonNode body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(uri).header("Content-Type", "application/json");
        if (key != null) request.header("X-API-Key", key);
        if (idempotencyKey != null) request.header("Idempotency-Key", idempotencyKey);
        return client.send(request.POST(HttpRequest.BodyPublishers.ofByteArray(mapper.writeValueAsBytes(body))).build(),
                HttpResponse.BodyHandlers.ofByteArray());
    }

    private HttpResponse<byte[]> get(URI uri, String key) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(uri).GET();
        if (key != null) request.header("X-API-Key", key);
        return client.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    private JsonNode awaitTerminal(URI uri, String key) throws Exception {
        Instant deadline = Instant.now().plusSeconds(20);
        do {
            HttpResponse<byte[]> response = get(uri, key);
            assertEquals(200, response.statusCode(), new String(response.body()));
            JsonNode value = mapper.readTree(response.body());
            String status = value.path("status").asText();
            if (status.equals("SUCCEEDED") || status.equals("FAILED") || status.equals("CANCELLED")) return value;
            Thread.sleep(20);
        } while (Instant.now().isBefore(deadline));
        throw new AssertionError("generation job did not reach terminal state");
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

    private ObjectNode aiRequest() {
        ObjectNode value = mapper.createObjectNode();
        value.put("mode", "AI_FREEFORM");
        value.put("outputFormat", "HTML");
        value.put("instruction", "生成系统评估报告");
        value.putObject("context").put("system", "omni-office");
        return value;
    }
}
