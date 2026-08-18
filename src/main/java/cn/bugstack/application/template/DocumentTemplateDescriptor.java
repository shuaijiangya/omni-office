package cn.bugstack.application.template;

/** 不包含模板正文的安全发现信息。 */
public final class DocumentTemplateDescriptor {

    private final String templateId;
    private final String version;
    private final String name;
    private final String description;

    public DocumentTemplateDescriptor(String templateId, String version, String name, String description) {
        this.templateId = templateId;
        this.version = version;
        this.name = name;
        this.description = description;
    }

    public String getTemplateId() {
        return templateId;
    }

    public String getVersion() {
        return version;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }
}
