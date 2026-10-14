package cn.bugstack.application.completion;

/** 已有表格单元格的覆盖策略。 */
public enum TableOverwritePolicy {
    /** 只填写空白单元格，并保留已有人工内容。 */
    BLANKS_ONLY,
    /** 使用计划值覆盖已有内容。 */
    OVERWRITE_ALL
}
