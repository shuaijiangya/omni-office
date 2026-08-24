package cn.bugstack.protocol.document.block;

import java.util.ArrayList;
import java.util.List;

/**
 * 普通文本段落。
 */
public final class ParagraphBlockSpec extends BlockSpec {

    private String text;
    private String styleName;
    private String fontColor;
    private List<TextRangeSpec> textRanges = new ArrayList<>();

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

    public String getFontColor() {
        return fontColor;
    }

    public void setFontColor(String fontColor) {
        this.fontColor = fontColor;
    }

    public List<TextRangeSpec> getTextRanges() {
        return textRanges;
    }

    public void setTextRanges(List<TextRangeSpec> textRanges) {
        this.textRanges = textRanges == null ? new ArrayList<>() : textRanges;
    }
}
