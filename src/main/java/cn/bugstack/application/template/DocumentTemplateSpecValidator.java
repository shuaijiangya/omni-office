package cn.bugstack.application.template;

import cn.bugstack.protocol.template.DocumentTemplateSpec;
import cn.bugstack.protocol.template.DocumentTemplateSpecVersion;
import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** 注册模板前执行标识、版本、Schema 和 DocumentSpec 骨架校验。 */
public final class DocumentTemplateSpecValidator {

    private static final Pattern TEMPLATE_ID = Pattern.compile("[a-z][a-z0-9]*(?:[.-][a-z0-9]+)*");
    private static final Pattern VERSION = Pattern.compile("[0-9]+\\.[0-9]+\\.[0-9]+(?:-[0-9A-Za-z.-]+)?");
    private final JsonSchemaFactory schemaFactory;

    public DocumentTemplateSpecValidator() {
        this(JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012));
    }

    public DocumentTemplateSpecValidator(JsonSchemaFactory schemaFactory) {
        if (schemaFactory == null) {
            throw new IllegalArgumentException("json schema factory must not be null");
        }
        this.schemaFactory = schemaFactory;
    }

    public List<DocumentTemplateViolation> validate(DocumentTemplateSpec spec) {
        List<DocumentTemplateViolation> errors = new ArrayList<>();
        if (spec == null) {
            errors.add(error("/", "REQUIRED", "document template spec must not be null"));
            return errors;
        }
        if (!DocumentTemplateSpecVersion.V1.equals(spec.getSchemaVersion())) {
            errors.add(error("/schemaVersion", "UNSUPPORTED_VERSION", "supported template schema version is 1.0"));
        }
        if (!matches(spec.getTemplateId(), TEMPLATE_ID) || spec.getTemplateId().length() > 64) {
            errors.add(error("/templateId", "INVALID_ID",
                    "template id must be a lowercase dot/dash separated identifier up to 64 characters"));
        }
        if (!matches(spec.getVersion(), VERSION) || spec.getVersion().length() > 64) {
            errors.add(error("/version", "INVALID_VERSION", "template version must be an explicit semantic version"));
        }
        if (!hasText(spec.getName())) {
            errors.add(error("/name", "REQUIRED", "template name must not be blank"));
        }
        validateDataSchema(spec.getDataSchema(), errors);
        validateDocumentTemplate(spec.getDocumentTemplate(), errors);
        return errors;
    }

    public void validateOrThrow(DocumentTemplateSpec spec) {
        List<DocumentTemplateViolation> errors = validate(spec);
        if (!errors.isEmpty()) {
            throw new DocumentTemplateValidationException("invalid document template definition", errors);
        }
    }

    private void validateDataSchema(JsonNode schema, List<DocumentTemplateViolation> errors) {
        if (schema == null || !schema.isObject()) {
            errors.add(error("/dataSchema", "REQUIRED", "template data schema must be a JSON object"));
            return;
        }
        JsonNode dialect = schema.get("$schema");
        if (dialect == null || !dialect.isTextual()
                || !dialect.asText().startsWith("https://json-schema.org/draft/2020-12/schema")) {
            errors.add(error("/dataSchema/$schema", "UNSUPPORTED_SCHEMA_DIALECT",
                    "template data schema must declare JSON Schema draft 2020-12"));
        }
        if (!"object".equals(schema.path("type").asText())) {
            errors.add(error("/dataSchema/type", "INVALID_ROOT_TYPE", "template data root type must be object"));
        }
        int errorsBeforeReferences = errors.size();
        validateReferences(schema, "/dataSchema", errors);
        if (errors.size() == errorsBeforeReferences) {
            try {
                schemaFactory.getSchema(schema);
            } catch (RuntimeException e) {
                errors.add(error("/dataSchema", "INVALID_SCHEMA",
                        "template data schema could not be compiled: " + e.getMessage()));
            }
        }
    }

    private void validateReferences(JsonNode node, String path, List<DocumentTemplateViolation> errors) {
        if (node == null) {
            return;
        }
        if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                validateReferences(node.get(i), path + "/" + i, errors);
            }
            return;
        }
        if (!node.isObject()) {
            return;
        }
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            String childPath = path + "/" + field.getKey().replace("~", "~0").replace("/", "~1");
            if (("$ref".equals(field.getKey()) || "$dynamicRef".equals(field.getKey()))
                    && field.getValue().isTextual() && !field.getValue().asText().startsWith("#")) {
                errors.add(error(childPath, "EXTERNAL_REFERENCE_NOT_ALLOWED",
                        "template data schema references must remain inside the registered schema"));
            }
            validateReferences(field.getValue(), childPath, errors);
        }
    }

    private void validateDocumentTemplate(JsonNode template, List<DocumentTemplateViolation> errors) {
        if (template == null || !template.isObject()) {
            errors.add(error("/documentTemplate", "REQUIRED", "document template must be a JSON object"));
            return;
        }
        if (!"1.0".equals(template.path("schemaVersion").asText())) {
            errors.add(error("/documentTemplate/schemaVersion", "REQUIRED",
                    "document template must produce DocumentSpec schema version 1.0"));
        }
        requireTemplateProperty(template, "metadata", errors);
        requireTemplateProperty(template, "layout", errors);
        requireTemplateProperty(template, "sections", errors);
    }

    private void requireTemplateProperty(JsonNode template, String name, List<DocumentTemplateViolation> errors) {
        if (!template.has(name) || template.get(name).isNull()) {
            errors.add(error("/documentTemplate/" + name, "REQUIRED",
                    "document template requires property: " + name));
        }
    }

    private boolean matches(String value, Pattern pattern) {
        return value != null && pattern.matcher(value).matches();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private DocumentTemplateViolation error(String path, String code, String message) {
        return new DocumentTemplateViolation(path, code, message);
    }
}
