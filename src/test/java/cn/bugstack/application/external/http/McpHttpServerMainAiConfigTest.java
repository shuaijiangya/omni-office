package cn.bugstack.application.external.http;

import cn.bugstack.application.ai.StructuredAiClient;
import cn.bugstack.application.ai.StructuredAiRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** HTTP 服务入口的模型供应商配置测试。 */
class McpHttpServerMainAiConfigTest {

    @TempDir
    Path dataRoot;

    @Test
    void configuresOpenAiCompatibleEndpointAndAuthorization() throws Exception {
        AtomicReference<String> auth = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            auth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] body = "{\"choices\":[{\"message\":{\"content\":\"{\\\"ok\\\":true}\"}}]}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            Map<String, String> env = new HashMap<>();
            env.put("OMNI_OFFICE_OPENAI_MODEL", "compatible-model");
            env.put("OMNI_OFFICE_OPENAI_BASE_URL", "http://127.0.0.1:" + server.getAddress().getPort() + "/v1");
            env.put("OMNI_OFFICE_OPENAI_API_KEY", "test-key");
            StructuredAiClient client = McpHttpServerMain.internalAiClient(env, dataRoot);
            ObjectMapper mapper = new ObjectMapper();
            String output = client.generateJson(new StructuredAiRequest("test", "Return JSON", "Generate",
                    null, mapper.createObjectNode().put("type", "object"), 1, Collections.emptyList()));
            assertEquals("{\"ok\":true}", output);
            assertEquals("Bearer test-key", auth.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void rejectsConflictingModelsAndMissingOfficialApiKey() {
        Map<String, String> env = new HashMap<>();
        env.put("OMNI_OFFICE_OPENAI_MODEL", "cloud-model");
        env.put("OMNI_OFFICE_OLLAMA_MODEL", "local-model");
        assertThrows(IllegalArgumentException.class, () -> McpHttpServerMain.internalAiClient(env, dataRoot));
        env.remove("OMNI_OFFICE_OLLAMA_MODEL");
        assertThrows(IllegalArgumentException.class, () -> McpHttpServerMain.internalAiClient(env, dataRoot));
    }
}
