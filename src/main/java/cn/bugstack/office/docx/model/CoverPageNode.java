package cn.bugstack.office.docx.model;

/**
 * GJB 438C 文档封面节点。
 */
public class CoverPageNode implements DocxBlock {

    /** 封面文档名称。 */
    private final String documentName;
    /** 封面项目名称。 */
    private final String projectName;
    /** 封面版本号。 */
    private final String version;

    /**
     * 创建封面节点。
     *
     * @param documentName 文档名称
     * @param projectName 项目名称
     * @param version 文档版本
     */
    public CoverPageNode(String documentName, String projectName, String version) {
        this.documentName = documentName;
        this.projectName = projectName;
        this.version = version;
    }

    /**
     * 获取文档名称。
     *
     * @return 文档名称
     */
    public String getDocumentName() {
        return documentName;
    }

    /**
     * 获取项目名称。
     *
     * @return 项目名称
     */
    public String getProjectName() {
        return projectName;
    }

    /**
     * 获取文档版本。
     *
     * @return 文档版本
     */
    public String getVersion() {
        return version;
    }
}
