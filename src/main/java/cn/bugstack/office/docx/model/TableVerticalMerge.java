package cn.bugstack.office.docx.model;

/**
 * 表格单元格纵向合并方式。
 */
public enum TableVerticalMerge {

    /**
     * 不进行纵向合并。
     */
    NONE,

    /**
     * 纵向合并起始单元格。
     */
    FIRST,

    /**
     * 继续合并到上方单元格。
     */
    PREVIOUS
}
