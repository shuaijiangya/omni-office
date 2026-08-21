package cn.bugstack.application.external.http;

import cn.bugstack.application.external.security.RequestIdentity;
import cn.bugstack.application.external.security.StaticApiKeyAuthenticator;
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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TemplateManagementHttpTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void managesTenantTemplateLifecycleAndEnforcesFourEyes() throws Exception {
        Map<String, RequestIdentity> keys = new LinkedHashMap<>();
        keys.put("author-key", identity("tenant-a", "author"));
        keys.put("reviewer-key", identity("tenant-a", "reviewer"));
        keys.put("other-tenant-key", identity("tenant-b", "reviewer"));
        McpHttpServerConfig config = new McpHttpServerConfig(new InetSocketAddress("127.0.0.1", 0),
                Files.createTempDirectory("template-management-http"), Collections.emptySet(),
                2 * 1024 * 1024, 500, 4, Duration.ofSeconds(30), Duration.ofMinutes(5));
        try (McpHttpServer server = new McpHttpServer(config, new StaticApiKeyAuthenticator(keys))) {
            server.start();
            URI base = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
            URI collection = base.resolve("/v1/admin/templates");
            ObjectNode template = template();

            HttpResponse<byte[]> created = post(collection, "author-key", template);
            assertEquals(201, created.statusCode(), new String(created.body()));
            assertEquals("DRAFT", json(created).path("status").asText());
            assertTrue(created.headers().firstValue("Location").orElse("")
                    .endsWith("/custom.assessment/versions/1.0.0"));

            JsonNode drafts = json(get(base.resolve("/v1/admin/templates?status=DRAFT&limit=10"),
                    "author-key"));
            assertEquals(1, drafts.path("templates").size());
            URI revision = base.resolve("/v1/admin/templates/custom.assessment/versions/1.0.0");
            assertEquals(404, get(revision, "other-tenant-key").statusCode());

            assertEquals(200, postNoBody(URI.create(revision + "/submit"), "author-key").statusCode());
            ObjectNode comment = mapper.createObjectNode().put("comment", "reviewed");
            assertEquals(409, post(URI.create(revision + "/approve"), "author-key", comment).statusCode());
            JsonNode approved = json(post(URI.create(revision + "/approve"), "reviewer-key", comment));
            assertEquals("PUBLISHED", approved.path("status").asText());

            URI compare = base.resolve("/v1/admin/templates/custom.assessment/compare"
                    + "?fromVersion=1.0.0&toVersion=1.0.0");
            assertTrue(json(get(compare, "reviewer-key")).path("backwardCompatible").asBoolean());

            ObjectNode reason = mapper.createObjectNode().put("comment", "superseded");
            JsonNode retired = json(post(URI.create(revision + "/retire"), "reviewer-key", reason));
            assertEquals("RETIRED", retired.path("status").asText());

            JsonNode exampleData;
            try (InputStream input = getClass().getResourceAsStream(
                    "/document-template/1.0/example-assessment-data.json")) {
                exampleData = mapper.readTree(input);
            }
            URI validate = base.resolve(
                    "/v1/templates/custom.assessment/versions/1.0.0/validate-data");
            assertEquals(422, post(validate, "author-key", exampleData).statusCode());
        }
    }

    private ObjectNode template() throws Exception {
        try (InputStream input = getClass().getResourceAsStream(
                "/document-template/1.0/example-assessment-template.json")) {
            ObjectNode value = (ObjectNode) mapper.readTree(input);
            value.put("templateId", "custom.assessment");
            return value;
        }
    }

    private RequestIdentity identity(String tenant, String principal) {
        return new RequestIdentity(tenant, principal, Collections.singleton("*"));
    }

    private HttpResponse<byte[]> post(URI uri, String key, JsonNode body) throws Exception {
        return client.send(HttpRequest.newBuilder(uri).header("X-API-Key", key)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofByteArray(mapper.writeValueAsBytes(body))).build(),
                HttpResponse.BodyHandlers.ofByteArray());
    }

    private HttpResponse<byte[]> postNoBody(URI uri, String key) throws Exception {
        return client.send(HttpRequest.newBuilder(uri).header("X-API-Key", key)
                        .POST(HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.ofByteArray());
    }

    private HttpResponse<byte[]> get(URI uri, String key) throws Exception {
        return client.send(HttpRequest.newBuilder(uri).header("X-API-Key", key).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
    }

    private JsonNode json(HttpResponse<byte[]> response) throws Exception {
        assertTrue(response.statusCode() >= 200 && response.statusCode() < 300,
                response.statusCode() + ": " + new String(response.body()));
        return mapper.readTree(response.body());
    }
}
