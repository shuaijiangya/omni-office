package cn.bugstack.application.ai.openai;

import cn.bugstack.application.ai.StructuredAiRequest;
import cn.bugstack.application.ai.ollama.OllamaStructuredAiClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** OpenAI 兼容协议及 Ollama 旧入口的回归测试。 */
class OpenAiCompatibleStructuredAiClientTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final AtomicReference<String> requestBody = new AtomicReference<>();
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private HttpServer server;
    private int responseStatus = 200;
    private String responseBody = "{\"choices\":[{\"message\":{\"content\":\"{\\\"ok\\\":true}\"}}]}";

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(responseStatus, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void sendsStandardChatCompletionsRequestWithBearerToken() throws Exception {
        OpenAiCompatibleStructuredAiClient client = client("secret", baseUrl());

        assertEquals("{\"ok\":true}", client.generateJson(request()));

        JsonNode body = mapper.readTree(requestBody.get());
        assertEquals("demo-model", body.path("model").asText());
        assertFalse(body.path("stream").asBoolean(true));
        assertEquals(0.1D, body.path("temperature").asDouble());
        assertEquals("json_object", body.path("response_format").path("type").asText());
        assertEquals("system", body.path("messages").get(0).path("role").asText());
        assertEquals("user", body.path("messages").get(1).path("role").asText());
        String prompt = body.path("messages").get(1).path("content").asText();
        assertTrue(prompt.contains("OUTPUT FIELDS"));
        assertTrue(prompt.contains("\"ok\""));
        assertTrue(prompt.contains("validation error"));
        assertFalse(body.has("format"));
        assertFalse(body.has("options"));
        assertFalse(body.has("think"));
        assertEquals("Bearer secret", authorization.get());
    }

    @Test
    void acceptsOllamaLegacyEndpointButUsesOpenAiProtocol() throws Exception {
        OllamaStructuredAiClient client = new OllamaStructuredAiClient(
                URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/api/chat"),
                "qwen3.5:2b", Duration.ofSeconds(10), 0.1D, HttpClient.newHttpClient(), mapper);

        assertEquals("{\"ok\":true}", client.generateJson(request()));
        assertEquals("qwen3.5:2b", mapper.readTree(requestBody.get()).path("model").asText());
        assertEquals("none", mapper.readTree(requestBody.get()).path("reasoning_effort").asText());
        assertEquals("json_schema", mapper.readTree(requestBody.get())
                .path("response_format").path("type").asText());
        assertEquals("object", mapper.readTree(requestBody.get())
                .path("response_format").path("json_schema").path("schema").path("type").asText());
        assertEquals(null, authorization.get());
    }

    @Test
    void removesOnlyUnsupportedOllamaPatternsWithoutChangingLocalSchema() throws Exception {
        OllamaStructuredAiClient client = new OllamaStructuredAiClient(
                URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1"),
                "qwen3.5:2b", Duration.ofSeconds(10), 0.1D, HttpClient.newHttpClient(), mapper);
        com.fasterxml.jackson.databind.node.ObjectNode schema = mapper.createObjectNode().put("type", "object");
        schema.putObject("properties").putObject("name")
                .put("type", "string").put("pattern", ".*\\\\S.*");
        StructuredAiRequest request = new StructuredAiRequest("test", "Return JSON", "Generate",
                null, schema, 1, Collections.emptyList());

        assertEquals("{\"ok\":true}", client.generateJson(request));
        assertFalse(mapper.readTree(requestBody.get()).path("response_format")
                .path("json_schema").path("schema").path("properties").path("name").has("pattern"));
        assertTrue(schema.path("properties").path("name").has("pattern"));
    }

    @Test
    void rejectsProviderFailuresWithoutEchoingResponseOrSecret() {
        responseStatus = 401;
        responseBody = "{\"error\":{\"message\":\"secret provider details\"}}";
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> client("secret", baseUrl()).generateJson(request()));
        assertTrue(error.getMessage().contains("HTTP 401"));
        assertFalse(error.getMessage().contains("secret"));
        assertFalse(error.getMessage().contains("provider details"));
    }

    @Test
    void rejectsMissingContentAndInvalidEndpoint() {
        responseBody = "{\"choices\":[{\"message\":{\"content\":null}}]}";
        assertThrows(IllegalStateException.class, () -> client(null, baseUrl()).generateJson(request()));
        assertThrows(IllegalArgumentException.class,
                () -> client(null, URI.create("http://127.0.0.1:11434/api/chat")));
    }

    private OpenAiCompatibleStructuredAiClient client(String apiKey, URI endpoint) {
        return new OpenAiCompatibleStructuredAiClient(endpoint, "demo-model", apiKey,
                Duration.ofSeconds(10), 0.1D, HttpClient.newHttpClient(), mapper);
    }

    private URI baseUrl() {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1");
    }

    private StructuredAiRequest request() {
        return new StructuredAiRequest("test", "Return JSON.", "Generate the result.",
                mapper.createObjectNode().put("source", "business"),
                mapper.createObjectNode().put("type", "object")
                        .set("properties", mapper.createObjectNode()
                                .set("ok", mapper.createObjectNode().put("type", "boolean"))),
                2, Collections.singletonList("validation error"));
    }
}
