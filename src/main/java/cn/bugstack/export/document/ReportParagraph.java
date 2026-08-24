package cn.bugstack.export.document;

import java.util.ArrayList;
import java.util.List;

/**
 * 报告中的段落内容。
 */
public class ReportParagraph implements ReportElement {

    /** 段落正文。 */
    private String text;

    /** 段落样式名称，例如正文、引用或说明。 */
    private String styleName;

    /** 可选字体颜色，格式为 {@code #RRGGBB}。 */
    private String fontColor;

    /** 互相连续且可独立设置样式的文本范围。 */
    private List<ReportTextRange> textRanges = new ArrayList<>();

    /**
     * 创建空段落，供序列化框架使用。
     */
    public ReportParagraph() {
    }

    /**
     * 创建正文段落。
     *
     * @param text 段落正文
     */
    public ReportParagraph(String text) {
        this.text = text;
    }

    /**
     * 获取当前元素的类型。
     *
     * @return 段落元素类型
     */
    @Override
    public ReportElementType getElementType() {
        return ReportElementType.PARAGRAPH;
    }

    /**
     * 获取段落正文。
     *
     * @return 段落正文
     */
    public String getText() {
        return text;
    }

    /**
     * 设置段落正文。
     *
     * @param text 段落正文
     */
    public void setText(String text) {
        this.text = text;
    }

    /**
     * 获取段落样式名称。
     *
     * @return 段落样式名称
     */
    public String getStyleName() {
        return styleName;
    }

    /**
     * 设置段落样式名称。
     *
     * @param styleName 段落样式名称
     */
    public void setStyleName(String styleName) {
        this.styleName = styleName;
    }

    public String getFontColor() {
        return fontColor;
    }

    public void setFontColor(String fontColor) {
        this.fontColor = fontColor;
    }

    /**
     * 获取有序文本范围。
     *
     * @return 文本范围列表
     */
    public List<ReportTextRange> getTextRanges() {
        return textRanges;
    }

    /**
     * 设置有序文本范围。
     *
     * @param textRanges 文本范围列表
     */
    public void setTextRanges(List<ReportTextRange> textRanges) {
        this.textRanges = textRanges == null ? new ArrayList<>() : textRanges;
    }
}
