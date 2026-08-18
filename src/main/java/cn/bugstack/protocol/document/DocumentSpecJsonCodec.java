package cn.bugstack.protocol.document;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;

/**
 * DocumentSpec 的严格 JSON 编解码器。
 */
public final class DocumentSpecJsonCodec {

    private final ObjectMapper objectMapper;

    public DocumentSpecJsonCodec() {
        this(new ObjectMapper());
    }

    public DocumentSpecJsonCodec(ObjectMapper objectMapper) {
        if (objectMapper == null) {
            throw new IllegalArgumentException("object mapper must not be null");
        }
        this.objectMapper = objectMapper.copy()
                .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
    }

    public DocumentSpec read(String json) {
        if (json == null || json.trim().isEmpty()) {
            throw new IllegalArgumentException("document spec json must not be blank");
        }
        try {
            return bind(objectMapper.readTree(json));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("invalid document spec json: " + e.getOriginalMessage(), e);
        }
    }

    public DocumentSpec read(InputStream input) {
        if (input == null) {
            throw new IllegalArgumentException("document spec input stream must not be null");
        }
        try {
            return bind(objectMapper.readTree(input));
        } catch (IOException e) {
            throw new IllegalArgumentException("invalid document spec json: " + e.getMessage(), e);
        }
    }

    public String write(DocumentSpec spec) {
        if (spec == null) {
            throw new IllegalArgumentException("document spec must not be null");
        }
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(spec);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialize document spec", e);
        }
    }

    private DocumentSpec bind(JsonNode root) throws JsonProcessingException {
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException("document spec json root must be an object");
        }
        requireProperty(root, "schemaVersion");
        requireProperty(root, "metadata");
        requireProperty(root, "layout");
        requireProperty(root, "sections");
        return objectMapper.treeToValue(root, DocumentSpec.class);
    }

    private void requireProperty(JsonNode root, String property) {
        if (!root.has(property) || root.get(property).isNull()) {
            throw new IllegalArgumentException("document spec json requires property: " + property);
        }
    }
}
