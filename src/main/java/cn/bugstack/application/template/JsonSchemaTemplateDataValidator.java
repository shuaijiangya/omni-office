package cn.bugstack.application.template;

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** 基于 JSON Schema draft 2020-12 的模板数据校验器。 */
public final class JsonSchemaTemplateDataValidator implements TemplateDataValidator {

    private final JsonSchemaFactory schemaFactory;

    public JsonSchemaTemplateDataValidator() {
        this(JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012));
    }

    public JsonSchemaTemplateDataValidator(JsonSchemaFactory schemaFactory) {
        if (schemaFactory == null) {
            throw new IllegalArgumentException("json schema factory must not be null");
        }
        this.schemaFactory = schemaFactory;
    }

    @Override
    public List<DocumentTemplateViolation> validate(JsonNode schemaNode, JsonNode data) {
        List<DocumentTemplateViolation> errors = new ArrayList<>();
        if (schemaNode == null || !schemaNode.isObject()) {
            errors.add(new DocumentTemplateViolation("/", "INVALID_SCHEMA",
                    "template data schema must be an object"));
            return errors;
        }
        if (data == null) {
            errors.add(new DocumentTemplateViolation("/", "REQUIRED", "template data must not be null"));
            return errors;
        }
        try {
            JsonSchema schema = schemaFactory.getSchema(schemaNode);
            Set<ValidationMessage> messages = schema.validate(data);
            messages.stream()
                    .sorted(Comparator.comparing(message -> message.getInstanceLocation().toString()
                            + "|" + message.getCode()))
                    .forEach(message -> errors.add(toViolation(message)));
        } catch (RuntimeException e) {
            errors.add(new DocumentTemplateViolation("/", "INVALID_SCHEMA",
                    "template data schema could not be compiled: " + e.getMessage()));
        }
        return errors;
    }

    private DocumentTemplateViolation toViolation(ValidationMessage message) {
        String path = message.getInstanceLocation() == null ? "/" : message.getInstanceLocation().toString();
        if (path == null || path.isEmpty() || "$".equals(path)) {
            path = "/";
        }
        String code = message.getCode() == null ? "SCHEMA_VIOLATION"
                : message.getCode().replace('.', '_').toUpperCase(Locale.ROOT);
        return new DocumentTemplateViolation(path, code, message.getMessage());
    }
}
