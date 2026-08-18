package cn.bugstack.protocol.template;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 版本化数据文档模板协议。
 *
 * <p>模板只描述输入数据约束和到 DocumentSpec 的结构映射，不承载领域计算。</p>
 */
public final class DocumentTemplateSpec {

    private String schemaVersion = DocumentTemplateSpecVersion.V1;
    private String templateId;
    private String version;
    private String name;
    private String description;
    private JsonNode dataSchema;
    private JsonNode documentTemplate;

    public String getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(String schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    public String getTemplateId() {
        return templateId;
    }

    public void setTemplateId(String templateId) {
        this.templateId = templateId;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public JsonNode getDataSchema() {
        return dataSchema;
    }

    public void setDataSchema(JsonNode dataSchema) {
        this.dataSchema = dataSchema;
    }

    public JsonNode getDocumentTemplate() {
        return documentTemplate;
    }

    public void setDocumentTemplate(JsonNode documentTemplate) {
        this.documentTemplate = documentTemplate;
    }
}
