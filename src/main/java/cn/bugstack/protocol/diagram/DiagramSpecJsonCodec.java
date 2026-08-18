package cn.bugstack.protocol.diagram;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;

/** DiagramSpec 的严格 JSON 编解码器。 */
public final class DiagramSpecJsonCodec {

    private final ObjectMapper objectMapper;

    public DiagramSpecJsonCodec() {
        this(new ObjectMapper());
    }

    public DiagramSpecJsonCodec(ObjectMapper objectMapper) {
        if (objectMapper == null) {
            throw new IllegalArgumentException("object mapper must not be null");
        }
        this.objectMapper = objectMapper.copy()
                .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
    }

    public DiagramSpec read(String json) {
        if (json == null || json.trim().isEmpty()) {
            throw new IllegalArgumentException("diagram spec json must not be blank");
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            if (root == null || !root.isObject()) {
                throw new IllegalArgumentException("diagram spec json root must be an object");
            }
            require(root, "schemaVersion");
            require(root, "type");
            require(root, "title");
            require(root, "nodes");
            return objectMapper.treeToValue(root, DiagramSpec.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("invalid diagram spec json: " + e.getOriginalMessage(), e);
        }
    }

    public DiagramSpec read(InputStream input) {
        if (input == null) {
            throw new IllegalArgumentException("diagram spec input stream must not be null");
        }
        try {
            JsonNode root = objectMapper.readTree(input);
            if (root == null || !root.isObject()) {
                throw new IllegalArgumentException("diagram spec json root must be an object");
            }
            require(root, "schemaVersion");
            require(root, "type");
            require(root, "title");
            require(root, "nodes");
            return objectMapper.treeToValue(root, DiagramSpec.class);
        } catch (IOException e) {
            throw new IllegalArgumentException("invalid diagram spec json: " + e.getMessage(), e);
        }
    }

    public String write(DiagramSpec spec) {
        if (spec == null) {
            throw new IllegalArgumentException("diagram spec must not be null");
        }
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(spec);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialize diagram spec", e);
        }
    }

    private void require(JsonNode root, String property) {
        if (!root.has(property) || root.get(property).isNull()) {
            throw new IllegalArgumentException("diagram spec json requires property: " + property);
        }
    }
}
