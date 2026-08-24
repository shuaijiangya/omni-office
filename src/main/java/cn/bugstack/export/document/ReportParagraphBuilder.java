package cn.bugstack.export.document;

import java.util.function.Consumer;

/**
 * 支持多个独立样式文本范围的报告段落构建器。
 */
public final class ReportParagraphBuilder {

    private final ReportSectionBuilder parent;
    private final ReportParagraph paragraph = new ReportParagraph();

    ReportParagraphBuilder(ReportSectionBuilder parent) {
        this.parent = parent;
    }

    /** 设置段落样式名称。 */
    public ReportParagraphBuilder style(String styleName) {
        paragraph.setStyleName(styleName);
        return this;
    }

    /** 设置所有文本范围继承的默认字体颜色。 */
    public ReportParagraphBuilder fontColor(String fontColor) {
        paragraph.setFontColor(fontColor);
        return this;
    }

    /** 追加完全继承段落样式的文本范围。 */
    public ReportParagraphBuilder text(String text) {
        paragraph.getTextRanges().add(new ReportTextRange(text));
        return this;
    }

    /**
     * 追加具有独立样式的文本范围。
     *
     * @param text 文本内容
     * @param styleCustomizer 文本范围样式配置
     * @return 当前构建器
     */
    public ReportParagraphBuilder text(String text, Consumer<ReportTextRangeStyle> styleCustomizer) {
        ReportTextRange range = new ReportTextRange(text);
        ReportTextRangeStyle style = new ReportTextRangeStyle();
        if (styleCustomizer != null) styleCustomizer.accept(style);
        range.setStyle(style);
        paragraph.getTextRanges().add(range);
        return this;
    }

    /** 完成当前段落并返回所属章节。 */
    public ReportSectionBuilder end() {
        return parent.add(paragraph);
    }
}
