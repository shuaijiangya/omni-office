package cn.bugstack.protocol.template;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;

/** DocumentTemplateSpec 的严格 JSON 编解码器。 */
public final class DocumentTemplateSpecJsonCodec {

    private final ObjectMapper objectMapper;

    public DocumentTemplateSpecJsonCodec() {
        this(new ObjectMapper());
    }

    public DocumentTemplateSpecJsonCodec(ObjectMapper objectMapper) {
        if (objectMapper == null) {
            throw new IllegalArgumentException("object mapper must not be null");
        }
        this.objectMapper = objectMapper.copy()
                .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
    }

    public DocumentTemplateSpec read(String json) {
        if (json == null || json.trim().isEmpty()) {
            throw new IllegalArgumentException("document template json must not be blank");
        }
        try {
            return bind(objectMapper.readTree(json));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("invalid document template json: " + e.getOriginalMessage(), e);
        }
    }

    public DocumentTemplateSpec read(InputStream input) {
        if (input == null) {
            throw new IllegalArgumentException("document template input stream must not be null");
        }
        try {
            return bind(objectMapper.readTree(input));
        } catch (IOException e) {
            throw new IllegalArgumentException("invalid document template json: " + e.getMessage(), e);
        }
    }

    public String write(DocumentTemplateSpec spec) {
        if (spec == null) {
            throw new IllegalArgumentException("document template spec must not be null");
        }
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(spec);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialize document template spec", e);
        }
    }

    private DocumentTemplateSpec bind(JsonNode root) throws JsonProcessingException {
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException("document template json root must be an object");
        }
        require(root, "schemaVersion");
        require(root, "templateId");
        require(root, "version");
        require(root, "name");
        require(root, "dataSchema");
        require(root, "documentTemplate");
        return objectMapper.treeToValue(root, DocumentTemplateSpec.class);
    }

    private void require(JsonNode root, String property) {
        if (!root.has(property) || root.get(property).isNull()) {
            throw new IllegalArgumentException("document template json requires property: " + property);
        }
    }
}
