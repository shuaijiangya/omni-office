package cn.bugstack.export.document;

/**
 * 报告文档元素类型。
 */
public enum ReportElementType {

    /** 动态章节标题及其子元素。 */
    SECTION,

    /** 普通文本内容。 */
    PARAGRAPH,

    /** 表格内容。 */
    TABLE,

    /** 图片内容。 */
    IMAGE,

    /** 可编辑 Visio 或其 PNG 预览图。 */
    DIAGRAM,

    /** 项目符号或编号列表项。 */
    LIST_ITEM,

    /** 显式分页符。 */
    PAGE_BREAK,

    /** 基于源码导出的类设计表格。 */
    CLASS_DESIGN_TABLE
}
