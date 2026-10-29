package cn.bugstack.application.ai.ollama;

import cn.bugstack.application.ai.StructuredAiClient;
import cn.bugstack.application.ai.StructuredAiRequest;
import cn.bugstack.application.ai.openai.OpenAiCompatibleStructuredAiClient;
import cn.bugstack.application.ai.openai.OpenAiCompatibleStructuredAiClient.ResponseFormatMode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Iterator;
import java.util.Map;

/**
 * Ollama 的 OpenAI 兼容协议适配器。
 *
 * <p>保留原有构造方法以兼容调用方；旧配置中的 {@code /api/chat} 地址会转换为
 * {@code /v1/chat/completions}，实际请求不再使用 Ollama 私有协议。</p>
 */
public final class OllamaStructuredAiClient implements StructuredAiClient {

    private static final URI DEFAULT_ENDPOINT = URI.create("http://127.0.0.1:11434/v1/chat/completions");

    private final OpenAiCompatibleStructuredAiClient delegate;

    /** 使用本机 Ollama 的 OpenAI 兼容端点。 */
    public OllamaStructuredAiClient(String model) {
        this(DEFAULT_ENDPOINT, model, Duration.ofMinutes(5), 0.1D,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(), new ObjectMapper());
    }

    /** 使用指定的 Ollama 兼容端点，支持旧版 {@code /api/chat} 配置。 */
    public OllamaStructuredAiClient(URI chatEndpoint, String model, Duration timeout,
                                    double temperature, HttpClient httpClient, ObjectMapper objectMapper) {
        delegate = new OpenAiCompatibleStructuredAiClient(compatibleEndpoint(chatEndpoint), model, null,
                timeout, temperature, "none", ResponseFormatMode.JSON_SCHEMA, httpClient, objectMapper);
    }

    @Override
    public String generateJson(StructuredAiRequest request) {
        if (request == null || request.getOutputSchema() == null) {
            return delegate.generateJson(request);
        }
        JsonNode modelSchema = request.getOutputSchema();
        removeUnsupportedPatterns(modelSchema);
        return delegate.generateJson(new StructuredAiRequest(request.getOperation(),
                request.getSystemInstruction(), request.getUserInstruction(), request.getContext(),
                modelSchema, request.getAttempt(), request.getValidationFeedback()));
    }

    /** Ollama 的 Schema 转换器只接受首尾锚定的 pattern；本地校验仍使用原始 Schema。 */
    private static void removeUnsupportedPatterns(JsonNode node) {
        if (node.isObject()) {
            ObjectNode object = (ObjectNode) node;
            JsonNode pattern = object.get("pattern");
            if (pattern != null && pattern.isTextual()
                    && (!pattern.asText().startsWith("^") || !pattern.asText().endsWith("$"))) {
                object.remove("pattern");
            }
            Iterator<Map.Entry<String, JsonNode>> fields = object.fields();
            while (fields.hasNext()) {
                removeUnsupportedPatterns(fields.next().getValue());
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                removeUnsupportedPatterns(child);
            }
        }
    }

    private static URI compatibleEndpoint(URI endpoint) {
        if (endpoint == null) {
            return null;
        }
        String value = endpoint.toString();
        if (value.endsWith("/api/chat")) {
            return URI.create(value.substring(0, value.length() - "/api/chat".length())
                    + "/v1/chat/completions");
        }
        return endpoint;
    }
}
