package cn.bugstack.protocol.document;

import java.util.ArrayList;
import java.util.List;

/**
 * 可由内部 AI、外部工具或普通 Java 调用方构造的通用文档协议。
 */
public final class DocumentSpec {

    private String schemaVersion = DocumentSpecVersion.V1;
    private DocumentMetadataSpec metadata = new DocumentMetadataSpec();
    private DocumentLayoutSpec layout = new DocumentLayoutSpec();
    private List<SectionSpec> sections = new ArrayList<>();

    public String getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(String schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    public DocumentMetadataSpec getMetadata() {
        return metadata;
    }

    public void setMetadata(DocumentMetadataSpec metadata) {
        this.metadata = metadata;
    }

    public DocumentLayoutSpec getLayout() {
        return layout;
    }

    public void setLayout(DocumentLayoutSpec layout) {
        this.layout = layout;
    }

    public List<SectionSpec> getSections() {
        return sections;
    }

    public void setSections(List<SectionSpec> sections) {
        this.sections = sections == null ? new ArrayList<>() : sections;
    }

    public DocumentSpec addSection(SectionSpec section) {
        if (section != null) {
            sections.add(section);
        }
        return this;
    }
}
