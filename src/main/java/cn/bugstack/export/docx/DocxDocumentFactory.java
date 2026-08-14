package cn.bugstack.export.docx;

import cn.bugstack.export.definition.ReportBlueprint;
import cn.bugstack.office.docx.api.DocxDocument;

/**
 * 根据报告蓝图创建目标 DOCX 文档的策略。
 *
 * <p>该扩展点用于接入企业模板、自定义 {@code StyleProfile} 或按报告类型动态设置
 * 页面、字体和前置内容，而无需修改 {@link DocxReportCompiler} 的编译逻辑。</p>
 */
@FunctionalInterface
public interface DocxDocumentFactory {

    /**
     * 创建用于本次报告编译的文档对象。
     *
     * @param blueprint 报告蓝图
     * @return 已完成基础样式配置的 DOCX 文档
     */
    DocxDocument create(ReportBlueprint blueprint);
}
