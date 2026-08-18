package cn.bugstack.export.api;

/**
 * 报告输出格式。
 */
public enum ReportOutputFormat {

    /** Microsoft Word docx 文档。 */
    DOCX,

    /** PDF 文档。 */
    PDF,

    /** 单文件 HTML 文档，图片以内联 Base64 形式保存。 */
    HTML
}
