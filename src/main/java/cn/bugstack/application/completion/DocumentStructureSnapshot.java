package cn.bugstack.application.completion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 上传 DOCX 的只读结构快照。
 *
 * <p>快照只包含供 AI 识别和确定性填充所需的结构信息，不暴露 Aspose 节点。</p>
 */
public final class DocumentStructureSnapshot {

    private final String fileName;
    private final String sha256;
    private final List<SourceTableSnapshot> tables;
    private final List<SourceParagraphSnapshot> paragraphs;

    /**
     * 创建文档结构快照。
     *
     * @param fileName 原始文件名
     * @param sha256 文件 SHA-256
     * @param tables 表格快照
     * @param paragraphs 正文直接段落快照
     */
    public DocumentStructureSnapshot(String fileName, String sha256, List<SourceTableSnapshot> tables,
                                     List<SourceParagraphSnapshot> paragraphs) {
        this.fileName = fileName;
        this.sha256 = sha256;
        this.tables = Collections.unmodifiableList(new ArrayList<>(tables));
        this.paragraphs = Collections.unmodifiableList(new ArrayList<>(paragraphs));
    }

    /** @return 原始文件名 */
    public String getFileName() {
        return fileName;
    }

    /** @return 文件 SHA-256 */
    public String getSha256() {
        return sha256;
    }

    /** @return 不可变表格快照列表 */
    public List<SourceTableSnapshot> getTables() {
        return tables;
    }

    /** @return 正文直接段落，不包含表格内部、页眉和页脚 */
    public List<SourceParagraphSnapshot> getParagraphs() {
        return paragraphs;
    }
}
