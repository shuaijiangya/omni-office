package cn.bugstack.protocol.document;

/** DocumentSpec 的标准 Word 封面配置。 */
public final class DocumentCoverSpec {
    private String documentName;
    private String projectName;
    private String version;

    public String getDocumentName() { return documentName; }
    public void setDocumentName(String documentName) { this.documentName = documentName; }
    public String getProjectName() { return projectName; }
    public void setProjectName(String projectName) { this.projectName = projectName; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
}
