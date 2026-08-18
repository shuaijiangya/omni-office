package cn.bugstack.application.template;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/** 使用模板声明的 JSON Schema 校验输入数据。 */
public interface TemplateDataValidator {

    List<DocumentTemplateViolation> validate(JsonNode schema, JsonNode data);

    default void validateOrThrow(JsonNode schema, JsonNode data) {
        List<DocumentTemplateViolation> errors = validate(schema, data);
        if (!errors.isEmpty()) {
            throw new DocumentTemplateValidationException("template data does not match schema", errors);
        }
    }
}
