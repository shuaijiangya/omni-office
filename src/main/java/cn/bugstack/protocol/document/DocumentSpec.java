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
    private DocumentCoverSpec cover;
    private List<DocumentRevisionSpec> revisionHistory = new ArrayList<>();
    private List<DocumentApprovalSpec> approvals = new ArrayList<>();

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

    public DocumentCoverSpec getCover() { return cover; }
    public void setCover(DocumentCoverSpec cover) { this.cover = cover; }
    public List<DocumentRevisionSpec> getRevisionHistory() { return revisionHistory; }
    public void setRevisionHistory(List<DocumentRevisionSpec> revisionHistory) {
        this.revisionHistory = revisionHistory == null ? new ArrayList<>() : revisionHistory;
    }
    public List<DocumentApprovalSpec> getApprovals() { return approvals; }
    public void setApprovals(List<DocumentApprovalSpec> approvals) {
        this.approvals = approvals == null ? new ArrayList<>() : approvals;
    }
}
