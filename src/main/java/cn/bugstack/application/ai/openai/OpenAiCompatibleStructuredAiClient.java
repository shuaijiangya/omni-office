package cn.bugstack.application.ai.openai;

import cn.bugstack.application.ai.StructuredAiClient;
import cn.bugstack.application.ai.StructuredAiRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * 使用 OpenAI Chat Completions 协议的结构化 JSON 客户端。
 *
 * <p>默认采用各服务均支持的 {@code response_format: json_object}，在提示词中提供输出字段
 * 提示；也可选择标准 {@code json_schema} 模式。实际 Schema 校验与重试仍由上层应用负责。
 * 兼容标准 OpenAI 接口及 Ollama 的 {@code /v1/chat/completions} 接口，不发送 Ollama 私有字段。</p>
 */
public final class OpenAiCompatibleStructuredAiClient implements StructuredAiClient {

    /** Chat Completions 的标准 JSON 输出约束方式。 */
    public enum ResponseFormatMode { JSON_OBJECT, JSON_SCHEMA }

    /** 官方 OpenAI Chat Completions 端点。 */
    public static final URI OPENAI_ENDPOINT = URI.create("https://api.openai.com/v1/chat/completions");

    private final URI chatEndpoint;
    private final String model;
    private final String apiKey;
    private final Duration timeout;
    private final double temperature;
    private final String reasoningEffort;
    private final ResponseFormatMode responseFormatMode;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    /** 使用官方 OpenAI Chat Completions 端点。 */
    public OpenAiCompatibleStructuredAiClient(String model, String apiKey) {
        this(OPENAI_ENDPOINT, model, apiKey, Duration.ofMinutes(5), 0.1D,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(), new ObjectMapper());
    }

    /**
     * 使用指定的兼容端点。端点可以是完整的 {@code /chat/completions} 地址，
     * 也可以是以 {@code /v1} 结尾的 base URL。
     */
    public OpenAiCompatibleStructuredAiClient(URI chatEndpoint, String model, String apiKey,
                                               Duration timeout, double temperature,
                                               HttpClient httpClient, ObjectMapper objectMapper) {
        this(chatEndpoint, model, apiKey, timeout, temperature, null, httpClient, objectMapper);
    }

    /** 为支持推理控制的兼容模型设置可选的标准 {@code reasoning_effort}。 */
    public OpenAiCompatibleStructuredAiClient(URI chatEndpoint, String model, String apiKey,
                                               Duration timeout, double temperature, String reasoningEffort,
                                               HttpClient httpClient, ObjectMapper objectMapper) {
        this(chatEndpoint, model, apiKey, timeout, temperature, reasoningEffort,
                ResponseFormatMode.JSON_OBJECT, httpClient, objectMapper);
    }

    /** 指定 JSON 输出模式；仅在服务和模型支持时使用 {@code JSON_SCHEMA}。 */
    public OpenAiCompatibleStructuredAiClient(URI chatEndpoint, String model, String apiKey,
                                               Duration timeout, double temperature, String reasoningEffort,
                                               ResponseFormatMode responseFormatMode,
                                               HttpClient httpClient, ObjectMapper objectMapper) {
        if (chatEndpoint == null || model == null || model.trim().isEmpty() || timeout == null
                || timeout.isZero() || timeout.isNegative() || responseFormatMode == null
                || httpClient == null || objectMapper == null) {
            throw new IllegalArgumentException("OpenAI endpoint, model, positive timeout, client and mapper are required");
        }
        if (!("http".equalsIgnoreCase(chatEndpoint.getScheme())
                || "https".equalsIgnoreCase(chatEndpoint.getScheme()))
                || chatEndpoint.getHost() == null || chatEndpoint.getUserInfo() != null
                || chatEndpoint.getQuery() != null || chatEndpoint.getFragment() != null) {
            throw new IllegalArgumentException("OpenAI endpoint must be an http(s) URL without credentials, query or fragment");
        }
        if (!Double.isFinite(temperature) || temperature < 0D || temperature > 2D) {
            throw new IllegalArgumentException("OpenAI temperature must be between 0 and 2");
        }
        this.chatEndpoint = normalizeEndpoint(chatEndpoint);
        this.model = model.trim();
        this.apiKey = apiKey == null ? null : apiKey.trim();
        this.timeout = timeout;
        this.temperature = temperature;
        this.reasoningEffort = reasoningEffort == null || reasoningEffort.trim().isEmpty()
                ? null : reasoningEffort.trim();
        this.responseFormatMode = responseFormatMode;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper.copy();
    }

    @Override
    public String generateJson(StructuredAiRequest request) {
        if (request == null || request.getOutputSchema() == null) {
            throw new IllegalArgumentException("structured AI request and output schema are required");
        }
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(chatEndpoint)
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            objectMapper.writeValueAsString(createBody(request)), StandardCharsets.UTF_8));
            if (apiKey != null && !apiKey.isEmpty()) {
                builder.header("Authorization", "Bearer " + apiKey);
            }
            HttpResponse<String> response = httpClient.send(builder.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("OpenAI-compatible endpoint returned HTTP " + response.statusCode());
            }
            JsonNode message = objectMapper.readTree(response.body()).path("choices").path(0).path("message");
            JsonNode refusal = message.path("refusal");
            if (refusal.isTextual() && !refusal.asText().trim().isEmpty()) {
                throw new IllegalStateException("OpenAI-compatible model refused the request");
            }
            JsonNode content = message.path("content");
            if (!content.isTextual() || content.asText().trim().isEmpty()) {
                throw new IllegalStateException("OpenAI-compatible response does not contain choices[0].message.content");
            }
            return content.asText();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("OpenAI-compatible request was interrupted", e);
        } catch (IOException e) {
            throw new IllegalStateException("failed to call OpenAI-compatible endpoint at " + chatEndpoint, e);
        }
    }

    private static URI normalizeEndpoint(URI endpoint) {
        String value = endpoint.toString();
        if (value.endsWith("/chat/completions")) {
            return endpoint;
        }
        if (value.endsWith("/v1") || value.endsWith("/v1/")) {
            return URI.create((value.endsWith("/") ? value : value + "/") + "chat/completions");
        }
        throw new IllegalArgumentException("OpenAI endpoint must end with /v1 or /chat/completions");
    }

    private ObjectNode createBody(StructuredAiRequest request) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);
        body.put("stream", false);
        body.put("temperature", temperature);
        if (reasoningEffort != null) {
            body.put("reasoning_effort", reasoningEffort);
        }
        ObjectNode responseFormat = body.putObject("response_format");
        if (responseFormatMode == ResponseFormatMode.JSON_SCHEMA) {
            responseFormat.put("type", "json_schema");
            ObjectNode jsonSchema = responseFormat.putObject("json_schema");
            jsonSchema.put("name", "structured_output");
            jsonSchema.put("strict", false);
            jsonSchema.set("schema", request.getOutputSchema());
        } else {
            responseFormat.put("type", "json_object");
        }
        ArrayNode messages = body.putArray("messages");
        messages.addObject().put("role", "system").put("content", request.getSystemInstruction());
        messages.addObject().put("role", "user").put("content", userMessage(request));
        return body;
    }

    private String userMessage(StructuredAiRequest request) {
        StringBuilder message = new StringBuilder();
        message.append("TASK\n").append(request.getUserInstruction()).append("\n\n");
        if (request.getContext() != null) {
            message.append("SOURCE CONTEXT\n").append(prettyJson(request.getContext())).append("\n\n");
        }
        if (!request.getValidationFeedback().isEmpty()) {
            message.append("VALIDATION ERRORS FROM THE PREVIOUS OUTPUT\n");
            for (String error : request.getValidationFeedback()) {
                message.append("- ").append(error).append('\n');
            }
            message.append("\nCorrect every error. Return the complete output object, not a patch.\n\n");
        }
        if (responseFormatMode == ResponseFormatMode.JSON_OBJECT) {
            message.append("OUTPUT FIELDS\n").append(schemaGuide(request.getOutputSchema()))
                    .append("\n\n");
        }
        message.append("OUTPUT RULE\nReturn only a JSON object that conforms to the requested output format. ")
                .append("Do not output the JSON Schema itself, schema metadata such as $schema, ")
                .append("this task, the source context, or these labels.");
        return message.toString();
    }

    private String schemaGuide(JsonNode schema) {
        JsonNode properties = schema.path("properties");
        if (!properties.isObject()) {
            return "Follow the requested JSON object format.";
        }
        StringBuilder guide = new StringBuilder();
        properties.fields().forEachRemaining(field -> {
            JsonNode definition = field.getValue();
            guide.append("- \"").append(field.getKey()).append("\": ")
                    .append(definition.path("type").asText("schema-defined value"));
            if (definition.hasNonNull("description")) {
                guide.append(". ").append(definition.path("description").asText());
            }
            guide.append('\n');
        });
        guide.append("Use only these top-level field names; fill their values, not their definitions.");
        return guide.toString();
    }

    private String prettyJson(JsonNode value) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialize structured AI input", e);
        }
    }
}
