package cn.bugstack.application.template;

import java.util.Objects;

/** 模板的稳定标识与显式版本。 */
public final class DocumentTemplateKey implements Comparable<DocumentTemplateKey> {

    private final String templateId;
    private final String version;

    public DocumentTemplateKey(String templateId, String version) {
        this.templateId = templateId;
        this.version = version;
    }

    public String getTemplateId() {
        return templateId;
    }

    public String getVersion() {
        return version;
    }

    @Override
    public int compareTo(DocumentTemplateKey other) {
        int idOrder = templateId.compareTo(other.templateId);
        return idOrder == 0 ? version.compareTo(other.version) : idOrder;
    }

    @Override
    public boolean equals(Object value) {
        if (this == value) {
            return true;
        }
        if (!(value instanceof DocumentTemplateKey)) {
            return false;
        }
        DocumentTemplateKey other = (DocumentTemplateKey) value;
        return Objects.equals(templateId, other.templateId) && Objects.equals(version, other.version);
    }

    @Override
    public int hashCode() {
        return Objects.hash(templateId, version);
    }

    @Override
    public String toString() {
        return templateId + "@" + version;
    }
}
