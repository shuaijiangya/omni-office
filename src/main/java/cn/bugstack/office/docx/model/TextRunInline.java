package cn.bugstack.office.docx.model;

import cn.bugstack.office.docx.style.RunStyle;

/**
 * 文本 run 行内节点。
 */
public class TextRunInline implements DocxInline {

    /** 文本片段内容。 */
    private final String text;

    /** 可选的 run 级样式覆盖。 */
    private final RunStyle style;

    /**
     * 创建文本行内节点。
     *
     * @param text 文本内容
     */
    public TextRunInline(String text) {
        this(text, (RunStyle) null);
    }

    /**
     * 创建带字体颜色的文本行内节点。
     *
     * @param text 文本内容
     * @param fontColor 字体颜色，格式为 {@code #RRGGBB}；为空时继承当前样式
     */
    public TextRunInline(String text, String fontColor) {
        this(text, colorStyle(fontColor));
    }

    /**
     * 创建带独立 run 样式的文本行内节点。
     *
     * @param text 文本内容
     * @param style run 级样式；为空时完全继承段落样式
     */
    public TextRunInline(String text, RunStyle style) {
        this.text = text;
        this.style = style == null ? null : style.copy();
    }

    /**
     * 获取文本内容。
     *
     * @return 文本内容
     */
    public String getText() {
        return text;
    }

    /**
     * 获取字体颜色。
     *
     * @return {@code #RRGGBB}；未设置时为 {@code null}
     */
    public String getFontColor() {
        return style == null ? null : style.getColor();
    }

    /**
     * 获取当前文本范围的独立样式。
     *
     * @return 样式副本；未设置时为 {@code null}
     */
    public RunStyle getStyle() {
        return style == null ? null : style.copy();
    }

    /** 将兼容颜色参数转换为 run 样式。 */
    private static RunStyle colorStyle(String fontColor) {
        if (fontColor == null) return null;
        RunStyle style = new RunStyle();
        style.setColor(fontColor);
        return style;
    }
}
