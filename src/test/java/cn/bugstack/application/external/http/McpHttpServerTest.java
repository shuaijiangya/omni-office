package cn.bugstack.application.external.http;

import cn.bugstack.application.external.ExternalDocumentToolApplication;
import cn.bugstack.application.external.security.RequestIdentity;
import cn.bugstack.application.external.security.StaticApiKeyAuthenticator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpHttpServerTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void authenticatesSessionsRunsAsyncToolsAndIsolatesTenantArtifacts() throws Exception {
        Map<String, RequestIdentity> keys = new LinkedHashMap<>();
        keys.put("tenant-a-key", identity("tenant-a", "alice"));
        keys.put("tenant-a-other", identity("tenant-a", "bob"));
        keys.put("tenant-b-key", identity("tenant-b", "mallory"));
        McpHttpServerConfig config = new McpHttpServerConfig(new InetSocketAddress("127.0.0.1", 0),
                Files.createTempDirectory("mcp-http"), Collections.emptySet(), 2 * 1024 * 1024,
                500, 4, Duration.ofSeconds(30), Duration.ofMinutes(5));
        try (McpHttpServer server = new McpHttpServer(config, new StaticApiKeyAuthenticator(keys))) {
            server.start();
            URI endpoint = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/mcp");
            assertEquals(401, post(endpoint, null, null, initialize()).statusCode());
            assertEquals(403, postWithOrigin(endpoint, "tenant-a-key", "https://evil.example", initialize()).statusCode());

            HttpResponse<byte[]> initialized = post(endpoint, "tenant-a-key", null, initialize());
            assertEquals(200, initialized.statusCode());
            String session = initialized.headers().firstValue("MCP-Session-Id").orElseThrow();
            assertEquals("2025-11-25", mapper.readTree(initialized.body()).path("result")
                    .path("protocolVersion").asText());

            assertEquals(403, post(endpoint, "tenant-a-other", session, notification()).statusCode());
            assertEquals(202, post(endpoint, "tenant-a-key", session, notification()).statusCode());

            ObjectNode arguments;
            try (java.io.InputStream input = getClass().getResourceAsStream(
                    "/document-spec/1.0/example-simple.json")) {
                arguments = (ObjectNode) mapper.readTree(input);
            }
            arguments.put("outputFormat", "HTML");
            ObjectNode call = request(2, "tools/call");
            ObjectNode params = call.putObject("params");
            params.put("name", ExternalDocumentToolApplication.EXPORT_DOCUMENT);
            params.set("arguments", arguments);
            params.putObject("task").put("ttl", 60_000);
            JsonNode createTask = json(post(endpoint, "tenant-a-key", session, call));
            String taskId = createTask.path("result").path("task").path("taskId").asText();
            assertFalse(taskId.isBlank());

            ObjectNode get = request(3, "tasks/get");
            get.putObject("params").put("taskId", taskId);
            String status = json(post(endpoint, "tenant-a-key", session, get))
                    .path("result").path("status").asText();
            assertTrue(status.equals("working") || status.equals("completed"));

            ObjectNode result = request(4, "tasks/result");
            result.putObject("params").put("taskId", taskId);
            JsonNode taskResult = json(post(endpoint, "tenant-a-key", session, result));
            String resourceUri = taskResult.path("result").path("structuredContent")
                    .path("artifact").path("resourceUri").asText();
            assertTrue(resourceUri.startsWith("omni-office://artifacts/"));
            String artifactId = resourceUri.substring(resourceUri.lastIndexOf('/') + 1);
            URI download = endpoint.resolve("/artifacts/" + artifactId);
            HttpResponse<byte[]> html = get(download, "tenant-a-key");
            assertEquals(200, html.statusCode());
            assertEquals("text/html", html.headers().firstValue("Content-Type").orElseThrow());
            assertTrue(new String(html.body(), java.nio.charset.StandardCharsets.UTF_8)
                    .toLowerCase().contains("<html"));
            assertEquals(404, get(download, "tenant-b-key").statusCode());

            assertEquals(200, client.send(HttpRequest.newBuilder(endpoint.resolve("/health/ready"))
                    .GET().build(), HttpResponse.BodyHandlers.discarding()).statusCode());
            String metrics = client.send(HttpRequest.newBuilder(endpoint.resolve("/metrics"))
                    .GET().build(), HttpResponse.BodyHandlers.ofString()).body();
            assertTrue(metrics.contains("omni_office_artifact_downloads_total 1"));
        }
    }

    private RequestIdentity identity(String tenant, String principal) {
        return new RequestIdentity(tenant, principal, Collections.singleton("*"));
    }

    private HttpResponse<byte[]> post(URI endpoint, String key, String session, JsonNode body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(endpoint)
                .header("Accept", "application/json, text/event-stream")
                .header("Content-Type", "application/json");
        if (key != null) request.header("X-API-Key", key);
        if (session != null) request.header("MCP-Session-Id", session)
                .header("MCP-Protocol-Version", "2025-11-25");
        return client.send(request.POST(HttpRequest.BodyPublishers.ofByteArray(mapper.writeValueAsBytes(body))).build(),
                HttpResponse.BodyHandlers.ofByteArray());
    }

    private HttpResponse<byte[]> postWithOrigin(URI endpoint, String key, String origin, JsonNode body) throws Exception {
        return client.send(HttpRequest.newBuilder(endpoint).header("X-API-Key", key)
                        .header("Origin", origin).header("Accept", "application/json, text/event-stream")
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofByteArray(mapper.writeValueAsBytes(body))).build(),
                HttpResponse.BodyHandlers.ofByteArray());
    }

    private HttpResponse<byte[]> get(URI endpoint, String key) throws Exception {
        return client.send(HttpRequest.newBuilder(endpoint).header("X-API-Key", key).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
    }

    private JsonNode json(HttpResponse<byte[]> response) throws Exception {
        assertEquals(200, response.statusCode(), new String(response.body()));
        return mapper.readTree(response.body());
    }

    private ObjectNode initialize() {
        ObjectNode value = request(1, "initialize");
        ObjectNode params = value.putObject("params");
        params.put("protocolVersion", "2025-11-25");
        params.putObject("capabilities");
        params.putObject("clientInfo").put("name", "test").put("version", "1");
        return value;
    }

    private ObjectNode notification() {
        ObjectNode value = mapper.createObjectNode();
        value.put("jsonrpc", "2.0");
        value.put("method", "notifications/initialized");
        return value;
    }

    private ObjectNode request(int id, String method) {
        ObjectNode value = mapper.createObjectNode();
        value.put("jsonrpc", "2.0");
        value.put("id", id);
        value.put("method", method);
        return value;
    }
}
