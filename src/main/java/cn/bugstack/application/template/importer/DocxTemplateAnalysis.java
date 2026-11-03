package cn.bugstack.application.template.importer;

import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * 上传 DOCX 的结构分析结果。
 *
 * <p>结构快照、内容模板草案与样式绑定分别保存，避免让模型修改原文档的物理样式。</p>
 */
public final class DocxTemplateAnalysis {

    private final ObjectNode structure;
    private final ObjectNode reportTemplate;
    private final ObjectNode styleBindings;

    /** 创建不可从外部修改的模板分析结果。 */
    public DocxTemplateAnalysis(ObjectNode structure, ObjectNode reportTemplate, ObjectNode styleBindings) {
        if (structure == null || reportTemplate == null || styleBindings == null) {
            throw new IllegalArgumentException("template analysis parts must not be null");
        }
        this.structure = structure.deepCopy();
        this.reportTemplate = reportTemplate.deepCopy();
        this.styleBindings = styleBindings.deepCopy();
    }

    /** @return 模板的章节、元素、占位符、样式和审核问题快照 */
    public ObjectNode getStructure() {
        return structure.deepCopy();
    }

    /** @return 可人工修改的逐标题 AI 内容模板草案 */
    public ObjectNode getReportTemplate() {
        return reportTemplate.deepCopy();
    }

    /** @return 以章节 ID 关联原 DOCX 锚点和样式样例的绑定草案 */
    public ObjectNode getStyleBindings() {
        return styleBindings.deepCopy();
    }
}
