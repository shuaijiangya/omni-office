package cn.bugstack.application.completion;

/** DOCX 正文段落补全操作。 */
public enum ParagraphPatchOperation {
    /** 替换目标段落的普通文本。 */
    REPLACE_TEXT,
    /** 在目标段落之后插入一个或多个正文段落。 */
    INSERT_AFTER
}
