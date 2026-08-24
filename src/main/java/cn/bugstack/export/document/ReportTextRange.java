package cn.bugstack.export.document;

/**
 * 报告段落中的一个有序文本范围。
 */
public class ReportTextRange {

    private String text;
    private ReportTextRangeStyle style;

    /** 创建空文本范围。 */
    public ReportTextRange() {
    }

    /**
     * 创建文本范围。
     *
     * @param text 文本内容
     */
    public ReportTextRange(String text) {
        this.text = text;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public ReportTextRangeStyle getStyle() {
        return style;
    }

    public void setStyle(ReportTextRangeStyle style) {
        this.style = style;
    }
}
