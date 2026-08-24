package cn.bugstack.protocol.document.block;

/**
 * DocumentSpec 段落中的一个有序文本范围。
 */
public final class TextRangeSpec {

    private String text;
    private TextRangeStyleSpec style;

    /** 创建空文本范围，供 JSON 反序列化使用。 */
    public TextRangeSpec() {
    }

    /**
     * 创建文本范围。
     *
     * @param text 文本内容
     */
    public TextRangeSpec(String text) {
        this.text = text;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public TextRangeStyleSpec getStyle() {
        return style;
    }

    public void setStyle(TextRangeStyleSpec style) {
        this.style = style;
    }
}
