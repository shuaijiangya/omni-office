package cn.bugstack.office.docx.style;

/**
 * 文本 run 样式定义。
 */
public class RunStyle {

    /** 文本字体名称，同时应用于西文和东亚文字。 */
    private String fontFamily;
    /** ASCII 英文和数字字体；为空时继承 {@link #fontFamily}。 */
    private String asciiFontFamily;
    /** 东亚文字字体；为空时继承 {@link #fontFamily}。 */
    private String farEastFontFamily;
    /** 字号，单位为磅；为空时继承段落样式。 */
    private Double fontSize;
    /** 是否加粗文本。 */
    private boolean bold;
    /** 是否显式设置加粗属性。 */
    private boolean boldSet;
    /** 是否倾斜文本。 */
    private boolean italic;
    /** 是否显式设置斜体属性。 */
    private boolean italicSet;
    /** 是否显示单下划线。 */
    private boolean underline;
    /** 是否显式设置下划线属性。 */
    private boolean underlineSet;
    /** 文本颜色。 */
    private String color;

    /**
     * 创建默认文本 run 样式。
     */
    public RunStyle() {
    }

    /**
     * 创建当前 run 样式的副本。
     *
     * @return 样式副本
     */
    public RunStyle copy() {
        RunStyle copy = new RunStyle();
        copy.fontFamily = fontFamily;
        copy.asciiFontFamily = asciiFontFamily;
        copy.farEastFontFamily = farEastFontFamily;
        copy.fontSize = fontSize;
        copy.bold = bold;
        copy.boldSet = boldSet;
        copy.italic = italic;
        copy.italicSet = italicSet;
        copy.underline = underline;
        copy.underlineSet = underlineSet;
        copy.color = color;
        return copy;
    }

    /**
     * 获取字体名称。
     *
     * @return 字体名称；未设置时为 {@code null}
     */
    public String getFontFamily() {
        return fontFamily;
    }

    /**
     * 设置字体名称。
     *
     * @param fontFamily 字体名称；为空时继承段落样式
     */
    public void setFontFamily(String fontFamily) {
        this.fontFamily = fontFamily;
    }

    /**
     * 获取 ASCII 英文和数字字体。
     *
     * @return ASCII 字体名称；未设置时为 {@code null}
     */
    public String getAsciiFontFamily() {
        return asciiFontFamily;
    }

    /**
     * 设置 ASCII 英文和数字字体。
     *
     * @param asciiFontFamily 字体名称；为空时继承通用字体
     */
    public void setAsciiFontFamily(String asciiFontFamily) {
        this.asciiFontFamily = asciiFontFamily;
    }

    /**
     * 获取东亚文字字体。
     *
     * @return 东亚字体名称；未设置时为 {@code null}
     */
    public String getFarEastFontFamily() {
        return farEastFontFamily;
    }

    /**
     * 设置东亚文字字体。
     *
     * @param farEastFontFamily 字体名称；为空时继承通用字体
     */
    public void setFarEastFontFamily(String farEastFontFamily) {
        this.farEastFontFamily = farEastFontFamily;
    }

    /**
     * 获取字号。
     *
     * @return 字号，单位为磅；未设置时为 {@code null}
     */
    public Double getFontSize() {
        return fontSize;
    }

    /**
     * 设置字号。
     *
     * @param fontSize 字号，单位为磅；必须为有限正数
     */
    public void setFontSize(Double fontSize) {
        if (fontSize != null && (!Double.isFinite(fontSize) || fontSize <= 0)) {
            throw new IllegalArgumentException("run fontSize must be a finite positive number");
        }
        this.fontSize = fontSize;
    }

    /**
     * 判断是否加粗。
     *
     * @return 加粗返回 {@code true}
     */
    public boolean isBold() {
        return bold;
    }

    /**
     * 设置是否加粗。
     *
     * @param bold 是否加粗
     */
    public void setBold(boolean bold) {
        this.bold = bold;
        this.boldSet = true;
    }

    /**
     * 判断是否显式设置加粗属性。
     *
     * @return 已显式设置时返回 {@code true}
     */
    public boolean isBoldSet() {
        return boldSet;
    }

    /**
     * 判断是否斜体。
     *
     * @return 斜体返回 {@code true}
     */
    public boolean isItalic() {
        return italic;
    }

    /**
     * 设置是否斜体。
     *
     * @param italic 是否斜体
     */
    public void setItalic(boolean italic) {
        this.italic = italic;
        this.italicSet = true;
    }

    /**
     * 判断是否显式设置斜体属性。
     *
     * @return 已显式设置时返回 {@code true}
     */
    public boolean isItalicSet() {
        return italicSet;
    }

    /**
     * 判断是否显示下划线。
     *
     * @return 显示下划线时返回 {@code true}
     */
    public boolean isUnderline() {
        return underline;
    }

    /**
     * 设置是否显示单下划线。
     *
     * @param underline 是否显示下划线
     */
    public void setUnderline(boolean underline) {
        this.underline = underline;
        this.underlineSet = true;
    }

    /**
     * 判断是否显式设置下划线属性。
     *
     * @return 已显式设置时返回 {@code true}
     */
    public boolean isUnderlineSet() {
        return underlineSet;
    }

    /**
     * 获取颜色值。
     *
     * @return 颜色值，通常为十六进制 RGB 字符串
     */
    public String getColor() {
        return color;
    }

    /**
     * 设置颜色值。
     *
     * @param color 颜色值，通常为十六进制 RGB 字符串
     */
    public void setColor(String color) {
        if (color != null && !color.matches("#[0-9A-Fa-f]{6}")) {
            throw new IllegalArgumentException("run color must use #RRGGBB format: " + color);
        }
        this.color = color == null ? null : color.toUpperCase(java.util.Locale.ROOT);
    }
}
