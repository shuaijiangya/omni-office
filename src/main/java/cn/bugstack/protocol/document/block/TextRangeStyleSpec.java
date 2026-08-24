package cn.bugstack.protocol.document.block;

/**
 * DocumentSpec 文本范围的独立样式。
 *
 * <p>所有字段均可选；未提供的属性继承所属段落样式。</p>
 */
public final class TextRangeStyleSpec {

    /** 同时作用于所有文字的通用字体。 */
    private String fontFamily;
    /** ASCII 英文和数字字体。 */
    private String asciiFontFamily;
    /** 中文等东亚文字字体。 */
    private String farEastFontFamily;
    private Double fontSize;
    private String fontColor;
    private Boolean bold;
    private Boolean italic;
    private Boolean underline;

    /** @return 通用字体名称 */
    public String getFontFamily() {
        return fontFamily;
    }

    /** @param fontFamily 通用字体名称 */
    public void setFontFamily(String fontFamily) {
        this.fontFamily = fontFamily;
    }

    /** @return ASCII 英文和数字字体名称 */
    public String getAsciiFontFamily() {
        return asciiFontFamily;
    }

    /** @param asciiFontFamily ASCII 英文和数字字体名称 */
    public void setAsciiFontFamily(String asciiFontFamily) {
        this.asciiFontFamily = asciiFontFamily;
    }

    /** @return 中文等东亚文字字体名称 */
    public String getFarEastFontFamily() {
        return farEastFontFamily;
    }

    /** @param farEastFontFamily 中文等东亚文字字体名称 */
    public void setFarEastFontFamily(String farEastFontFamily) {
        this.farEastFontFamily = farEastFontFamily;
    }

    public Double getFontSize() {
        return fontSize;
    }

    public void setFontSize(Double fontSize) {
        this.fontSize = fontSize;
    }

    public String getFontColor() {
        return fontColor;
    }

    public void setFontColor(String fontColor) {
        this.fontColor = fontColor;
    }

    public Boolean getBold() {
        return bold;
    }

    public void setBold(Boolean bold) {
        this.bold = bold;
    }

    public Boolean getItalic() {
        return italic;
    }

    public void setItalic(Boolean italic) {
        this.italic = italic;
    }

    public Boolean getUnderline() {
        return underline;
    }

    public void setUnderline(Boolean underline) {
        this.underline = underline;
    }
}
