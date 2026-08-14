package cn.bugstack.export.docx;

import cn.bugstack.export.definition.ReportBlueprint;
import cn.bugstack.office.docx.builder.SectionBuilder;

/**
 * 自定义报告元素的 docx 编译上下文。
 */
public final class DocxReportCompileContext {

    /** 当前 DOCX 章节构建器。 */
    private final SectionBuilder section;
    /** 报告蓝图定义。 */
    private final ReportBlueprint blueprint;
    /** 当前报告章节层级。 */
    private final int sectionLevel;

    DocxReportCompileContext(SectionBuilder section, ReportBlueprint blueprint, int sectionLevel) {
        this.section = section;
        this.blueprint = blueprint;
        this.sectionLevel = sectionLevel;
    }

    /**
     * 获取当前 docx 章节 Builder。
     *
     * @return docx 章节 Builder
     */
    public SectionBuilder getSection() {
        return section;
    }

    /**
     * 获取报告蓝图。
     *
     * @return 报告蓝图
     */
    public ReportBlueprint getBlueprint() {
        return blueprint;
    }

    /**
     * 获取当前语义章节层级。
     *
     * @return 标题层级，范围为 1 到 9
     */
    public int getSectionLevel() {
        return sectionLevel;
    }
}
