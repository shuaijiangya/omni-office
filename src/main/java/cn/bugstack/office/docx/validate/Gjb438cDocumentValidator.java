package cn.bugstack.office.docx.validate;

import cn.bugstack.office.docx.model.ApprovalPageNode;
import cn.bugstack.office.docx.model.CoverPageNode;
import cn.bugstack.office.docx.model.DocxBlock;
import cn.bugstack.office.docx.model.DocumentNode;
import cn.bugstack.office.docx.model.ParagraphNode;
import cn.bugstack.office.docx.model.RevisionHistoryNode;
import cn.bugstack.office.docx.model.SectionNode;
import cn.bugstack.office.docx.model.TemplateCoverPageNode;

/**
 * GJB 438C 文档结构校验器。
 *
 * <p>该校验器关注标准文档的骨架完整性，适合在业务侧生成正式文档前显式调用。</p>
 */
public class Gjb438cDocumentValidator {

    /**
     * 创建 GJB 438C 文档结构校验器。
     */
    public Gjb438cDocumentValidator() {
    }

    /**
     * 校验 GJB 438C 文档结构。
     *
     * @param document 文档根节点
     * @return 校验结果
     */
    public ValidationResult validate(DocumentNode document) {
        ValidationResult result = new DocxValidator().validate(document);
        if (!containsFrontMatter(document, CoverPageNode.class)
                && !containsFrontMatter(document, TemplateCoverPageNode.class)) {
            result.addMessage("GJB 438C document must contain a cover page");
        }
        if (!containsFrontMatter(document, RevisionHistoryNode.class)) {
            result.addMessage("GJB 438C document must contain revision history");
        }
        if (!containsFrontMatter(document, ApprovalPageNode.class)) {
            result.addMessage("GJB 438C document must contain approval page");
        }
        if (!document.isTableOfContentsEnabled()) {
            result.addMessage("GJB 438C document must enable table of contents");
        }
        if (!containsHeading1(document)) {
            result.addMessage("GJB 438C document must contain at least one Heading1");
        }
        return result;
    }

    /**
     * 判断文档前置内容中是否包含指定类型节点。
     *
     * @param document 待检查文档
     * @param type 节点类型
     * @return 存在指定类型节点时返回 {@code true}
     */
    private boolean containsFrontMatter(DocumentNode document, Class<? extends DocxBlock> type) {
        for (DocxBlock block : document.getFrontMatterBlocks()) {
            if (type.isInstance(block)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断正文是否至少包含一个一级标题。
     *
     * @param document 待检查文档
     * @return 包含一级标题时返回 {@code true}
     */
    private boolean containsHeading1(DocumentNode document) {
        for (SectionNode section : document.getSections()) {
            for (DocxBlock block : section.getBlocks()) {
                if (block instanceof ParagraphNode
                        && "Heading1".equals(((ParagraphNode) block).getStyleName())) {
                    return true;
                }
            }
        }
        return false;
    }
}
