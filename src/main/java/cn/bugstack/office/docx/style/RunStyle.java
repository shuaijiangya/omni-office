package cn.bugstack.office.docx.style;

/**
 * 文本 run 样式定义。
 */
public class RunStyle {

    /** 是否加粗文本。 */
    private boolean bold;
    /** 是否倾斜文本。 */
    private boolean italic;
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
        copy.bold = bold;
        copy.italic = italic;
        copy.color = color;
        return copy;
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
        this.color = color;
    }
}
