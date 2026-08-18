package cn.bugstack.application.schema;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 检查新 Schema 是否仍接受旧 Schema 的对象字段与必填约束。 */
public final class JsonSchemaCompatibilityChecker {

    public SchemaCompatibilityResult backwardCompatible(JsonNode previous, JsonNode candidate) {
        if (previous == null || candidate == null || !previous.isObject() || !candidate.isObject()) {
            throw new IllegalArgumentException("both JSON schemas must be objects");
        }
        List<String> errors = new ArrayList<>();
        compareObject(previous, candidate, "$", errors);
        return new SchemaCompatibilityResult(errors);
    }

    private void compareObject(JsonNode previous, JsonNode candidate, String path, List<String> errors) {
        Set<String> oldRequired = textSet(previous.path("required"));
        Set<String> newRequired = textSet(candidate.path("required"));
        for (String required : newRequired) {
            if (!oldRequired.contains(required)) {
                errors.add(path + ": newly required property is not backward compatible: " + required);
            }
        }
        JsonNode oldProperties = previous.path("properties");
        JsonNode newProperties = candidate.path("properties");
        if (!oldProperties.isObject()) {
            return;
        }
        Iterator<Map.Entry<String, JsonNode>> fields = oldProperties.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> oldField = fields.next();
            JsonNode newField = newProperties.path(oldField.getKey());
            String fieldPath = path + "." + oldField.getKey();
            if (newField.isMissingNode()) {
                errors.add(fieldPath + ": existing property was removed");
                continue;
            }
            String oldType = oldField.getValue().path("type").asText();
            String newType = newField.path("type").asText();
            if (!oldType.isEmpty() && !newType.isEmpty() && !oldType.equals(newType)) {
                errors.add(fieldPath + ": property type changed from " + oldType + " to " + newType);
            }
            if ("object".equals(oldType)) {
                compareObject(oldField.getValue(), newField, fieldPath, errors);
            }
        }
    }

    private Set<String> textSet(JsonNode values) {
        Set<String> result = new HashSet<>();
        if (values.isArray()) {
            values.forEach(value -> result.add(value.asText()));
        }
        return result;
    }
}
