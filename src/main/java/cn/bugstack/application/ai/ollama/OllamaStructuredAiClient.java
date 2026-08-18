package cn.bugstack.application.ai.ollama;

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
import java.time.Duration;

/**
 * Ollama {@code /api/chat} 的结构化 JSON 客户端。
 *
 * <p>输出 Schema 通过 Ollama 的 {@code format} 字段传递，模型正文只接收任务、上下文和
 * 上一次本地校验错误。该适配器不负责信任模型输出，所有结果仍由 M4 本地流水线校验。</p>
 */
public final class OllamaStructuredAiClient implements StructuredAiClient {

    private static final URI DEFAULT_ENDPOINT = URI.create("http://127.0.0.1:11434/api/chat");
    private final URI chatEndpoint;
    private final String model;
    private final Duration timeout;
    private final double temperature;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public OllamaStructuredAiClient(String model) {
        this(DEFAULT_ENDPOINT, model, Duration.ofMinutes(5), 0.1D,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(), new ObjectMapper());
    }

    public OllamaStructuredAiClient(URI chatEndpoint, String model, Duration timeout,
                                    double temperature, HttpClient httpClient, ObjectMapper objectMapper) {
        if (chatEndpoint == null || model == null || model.trim().isEmpty() || timeout == null
                || httpClient == null || objectMapper == null) {
            throw new IllegalArgumentException("Ollama endpoint, model, timeout, client and mapper are required");
        }
        if (!"http".equalsIgnoreCase(chatEndpoint.getScheme())
                && !"https".equalsIgnoreCase(chatEndpoint.getScheme())) {
            throw new IllegalArgumentException("Ollama endpoint must use http or https");
        }
        if (!Double.isFinite(temperature) || temperature < 0D || temperature > 2D) {
            throw new IllegalArgumentException("Ollama temperature must be between 0 and 2");
        }
        this.chatEndpoint = chatEndpoint;
        this.model = model.trim();
        this.timeout = timeout;
        this.temperature = temperature;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper.copy();
    }

    @Override
    public String generateJson(StructuredAiRequest request) {
        if (request == null || request.getOutputSchema() == null) {
            throw new IllegalArgumentException("structured AI request and output schema are required");
        }
        try {
            String requestBody = objectMapper.writeValueAsString(createBody(request));
            HttpRequest httpRequest = HttpRequest.newBuilder(chatEndpoint)
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();
            HttpResponse<String> response = httpClient.send(httpRequest,
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Ollama returned HTTP " + response.statusCode()
                        + ": " + abbreviate(response.body(), 2_000));
            }
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode content = root.path("message").path("content");
            if (!content.isTextual() || content.asText().trim().isEmpty()) {
                throw new IllegalStateException("Ollama response does not contain message.content");
            }
            return content.asText();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Ollama request was interrupted", e);
        } catch (IOException e) {
            throw new IllegalStateException("failed to call Ollama at " + chatEndpoint, e);
        }
    }

    private ObjectNode createBody(StructuredAiRequest request) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);
        body.put("stream", false);
        body.put("think", false);
        body.set("format", request.getOutputSchema());
        ObjectNode options = body.putObject("options");
        options.put("temperature", temperature);
        ArrayNode messages = body.putArray("messages");
        messages.addObject()
                .put("role", "system")
                .put("content", request.getSystemInstruction());
        messages.addObject()
                .put("role", "user")
                .put("content", userMessage(request));
        return body;
    }

    private String userMessage(StructuredAiRequest request) {
        StringBuilder message = new StringBuilder();
        message.append("TASK\n")
                .append(request.getUserInstruction())
                .append("\n\n");
        if (request.getContext() != null) {
            message.append("SOURCE CONTEXT\n")
                    .append(prettyJson(request.getContext()))
                    .append("\n\n");
        }
        if (!request.getValidationFeedback().isEmpty()) {
            message.append("VALIDATION ERRORS FROM THE PREVIOUS OUTPUT\n");
            for (String error : request.getValidationFeedback()) {
                message.append("- ").append(error).append('\n');
            }
            message.append('\n')
                    .append("Correct every error. Return the complete output object, not a patch.\n\n");
        }
        message.append("REQUIRED OUTPUT FIELDS\n")
                .append(schemaGuide(request.getOutputSchema()))
                .append("\n\n");
        message.append("OUTPUT RULE\nReturn only the object required by the output schema. "
                + "Do not repeat this task, the source context, or these labels.");
        return message.toString();
    }

    private String prettyJson(JsonNode value) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialize Ollama source context", e);
        }
    }

    private String schemaGuide(JsonNode schema) {
        StringBuilder guide = new StringBuilder();
        JsonNode properties = schema.path("properties");
        JsonNode required = schema.path("required");
        if (!properties.isObject()) {
            return "Follow every field and constraint in the supplied output format.";
        }
        java.util.Iterator<java.util.Map.Entry<String, JsonNode>> fields = properties.fields();
        while (fields.hasNext()) {
            java.util.Map.Entry<String, JsonNode> field = fields.next();
            JsonNode definition = field.getValue();
            guide.append("- ").append(field.getKey()).append(": ")
                    .append(describeType(definition));
            if (contains(required, field.getKey())) {
                guide.append(", required");
            }
            if (definition.has("minLength")) {
                guide.append(", minLength ").append(definition.path("minLength").asInt());
            }
            if (definition.has("maxLength")) {
                guide.append(", maxLength ").append(definition.path("maxLength").asInt());
            }
            if (definition.has("minItems")) {
                guide.append(", minItems ").append(definition.path("minItems").asInt());
            }
            if (definition.has("maxItems")) {
                guide.append(", maxItems ").append(definition.path("maxItems").asInt());
            }
            if (definition.hasNonNull("description")
                    && !definition.path("description").asText().trim().isEmpty()) {
                guide.append(". ").append(definition.path("description").asText().trim());
            }
            appendAllowedValues(guide, definition.path("enum"));
            if ("array".equals(definition.path("type").asText())) {
                appendAllowedValues(guide, definition.path("items").path("enum"));
            }
            guide.append('\n');
        }
        guide.append("Use exactly these top-level field names and no others.");
        return guide.toString();
    }

    private void appendAllowedValues(StringBuilder guide, JsonNode values) {
        if (!values.isArray() || values.isEmpty() || values.size() > 20) {
            return;
        }
        String serialized = values.toString();
        if (serialized.length() <= 4_000) {
            guide.append(". Allowed values: ").append(serialized);
        }
    }

    private String describeType(JsonNode definition) {
        String type = definition.path("type").asText();
        if ("array".equals(type)) {
            return "array of " + definition.path("items").path("type").asText("schema-defined values");
        }
        if (!type.isEmpty()) {
            return type;
        }
        return definition.has("$ref") ? "schema-defined object" : "schema-defined value";
    }

    private boolean contains(JsonNode array, String value) {
        if (!array.isArray()) {
            return false;
        }
        for (JsonNode item : array) {
            if (value.equals(item.asText())) {
                return true;
            }
        }
        return false;
    }

    private String abbreviate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
