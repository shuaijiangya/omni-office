package cn.bugstack.application.external.client;

import cn.bugstack.application.external.http.McpHttpServer;
import cn.bugstack.application.external.http.McpHttpServerConfig;
import cn.bugstack.application.external.security.RequestIdentity;
import cn.bugstack.application.external.security.StaticApiKeyAuthenticator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OmniOfficeGenerationClientTest {

    @Test
    void submitsPollsPagesDownloadsAndReturnsStructuredErrors() throws Exception {
        McpHttpServerConfig config = new McpHttpServerConfig(new InetSocketAddress("127.0.0.1", 0),
                Files.createTempDirectory("generation-sdk"), Collections.emptySet(), 2 * 1024 * 1024,
                500, 4, Duration.ofSeconds(30), Duration.ofMinutes(5));
        RequestIdentity identity = new RequestIdentity("tenant-a", "alice", Collections.singleton("*"));
        try (McpHttpServer server = new McpHttpServer(config,
                new StaticApiKeyAuthenticator(Map.of("sdk-test-key", identity)))) {
            server.start();
            URI root = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
            OmniOfficeGenerationClient client = OmniOfficeGenerationClient.apiKey(root, "sdk-test-key");
            JsonNode submitted = client.submit(documentRequest(), "sdk-request-1", "sdk-trace-1");
            JsonNode completed = client.awaitTerminal(submitted.path("jobId").asText(),
                    Duration.ofSeconds(20), Duration.ofMillis(20));
            assertEquals("SUCCEEDED", completed.path("status").asText(), completed.toString());
            assertEquals(1, client.list("SUCCEEDED", null, 1).path("jobs").size());
            String resourceUri = completed.path("artifacts").path(0).path("resourceUri").asText();
            assertFalse(resourceUri.isBlank());
            assertFalse(new String(client.download(resourceUri), java.nio.charset.StandardCharsets.UTF_8).isBlank());

            OmniOfficeApiException missing = assertThrows(OmniOfficeApiException.class,
                    () -> client.get("00000000-0000-0000-0000-000000000000"));
            assertEquals(404, missing.getStatus());
            assertEquals("GENERATION_JOB_NOT_FOUND", missing.getCode());
        }
    }

    private ObjectNode documentRequest() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode value = mapper.createObjectNode();
        value.put("mode", "DOCUMENT_SPEC");
        value.put("outputFormat", "HTML");
        try (InputStream input = getClass().getResourceAsStream("/document-spec/1.0/example-simple.json")) {
            value.set("documentSpec", mapper.readTree(input));
        }
        return value;
    }
}
