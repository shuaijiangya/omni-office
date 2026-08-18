package cn.bugstack.protocol.document.block;

/**
 * 普通文本段落。
 */
public final class ParagraphBlockSpec extends BlockSpec {

    private String text;
    private String styleName;

    public ParagraphBlockSpec() {
    }

    public ParagraphBlockSpec(String text) {
        this.text = text;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getStyleName() {
        return styleName;
    }

    public void setStyleName(String styleName) {
        this.styleName = styleName;
    }
}
