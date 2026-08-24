package cn.bugstack.office.docx.style;

/**
 * 表格样式定义。
 */
public class TableStyle {

    /** 默认表格字体。 */
    public static final String DEFAULT_FONT_FAMILY = "宋体";
    /** 默认表格 ASCII 英文和数字字体。 */
    public static final String DEFAULT_ASCII_FONT_FAMILY = "Times New Roman";
    /** 默认表格东亚文字字体。 */
    public static final String DEFAULT_FAR_EAST_FONT_FAMILY = DEFAULT_FONT_FAMILY;
    /** 默认表头东亚文字字体。 */
    public static final String DEFAULT_HEADER_FAR_EAST_FONT_FAMILY = "黑体";
    /** 默认表格字号，单位为磅。 */
    public static final double DEFAULT_FONT_SIZE = 10.5D;
    /** 默认表格文字颜色。 */
    public static final String DEFAULT_FONT_COLOR = "#000000";

    /** 表格样式名称。 */
    private final String name;
    /** 是否绘制表格边框。 */
    private boolean bordered;
    /** 是否在跨页时重复首行表头。 */
    private boolean repeatHeaderRow;
    /** 表头文本样式。 */
    private RunStyle headerTextStyle = defaultHeaderTextStyle();
    /** 表内容文本样式。 */
    private RunStyle bodyTextStyle = defaultTextStyle();

    /**
     * 创建表格样式。
     *
     * @param name 样式名称
     */
    public TableStyle(String name) {
        this.name = name;
    }

    /**
     * 创建当前表格样式的副本。
     *
     * @return 样式副本
     */
    public TableStyle copy() {
        TableStyle copy = new TableStyle(name);
        copy.bordered = bordered;
        copy.repeatHeaderRow = repeatHeaderRow;
        copy.headerTextStyle = headerTextStyle.copy();
        copy.bodyTextStyle = bodyTextStyle.copy();
        return copy;
    }

    /**
     * 获取样式名称。
     *
     * @return 样式名称
     */
    public String getName() {
        return name;
    }

    /**
     * 判断是否显示边框。
     *
     * @return 显示边框返回 {@code true}
     */
    public boolean isBordered() {
        return bordered;
    }

    /**
     * 设置是否显示边框。
     *
     * @param bordered 是否显示边框
     */
    public void setBordered(boolean bordered) {
        this.bordered = bordered;
    }

    /**
     * 判断表头是否加粗。
     *
     * @return 表头加粗返回 {@code true}
     */
    public boolean isHeaderBold() {
        return headerTextStyle.isBoldSet() && headerTextStyle.isBold();
    }

    /**
     * 设置表头是否加粗。
     *
     * @param headerBold 表头是否加粗
     */
    public void setHeaderBold(boolean headerBold) {
        headerTextStyle.setBold(headerBold);
        if (headerBold) {
            // 兼容旧版：此前 headerBold 同时隐式开启了跨页重复表头。
            repeatHeaderRow = true;
        }
    }

    /**
     * 判断是否在跨页时重复首行表头。
     *
     * @return 重复表头时返回 {@code true}
     */
    public boolean isRepeatHeaderRow() {
        return repeatHeaderRow;
    }

    /**
     * 设置是否在跨页时重复首行表头。
     *
     * @param repeatHeaderRow 是否重复首行表头
     */
    public void setRepeatHeaderRow(boolean repeatHeaderRow) {
        this.repeatHeaderRow = repeatHeaderRow;
    }

    /**
     * 获取表格默认字体。
     *
     * @return 字体名称
     */
    public String getFontFamily() {
        return bodyTextStyle.getFontFamily();
    }

    /**
     * 设置表格默认字体。
     *
     * @param fontFamily 非空字体名称
     */
    public void setFontFamily(String fontFamily) {
        if (fontFamily == null || fontFamily.trim().isEmpty()) {
            throw new IllegalArgumentException("table font family must not be blank");
        }
        String normalized = fontFamily.trim();
        headerTextStyle.setFontFamily(normalized);
        headerTextStyle.setAsciiFontFamily(normalized);
        headerTextStyle.setFarEastFontFamily(normalized);
        bodyTextStyle.setFontFamily(normalized);
        bodyTextStyle.setAsciiFontFamily(normalized);
        bodyTextStyle.setFarEastFontFamily(normalized);
    }

    /**
     * 获取可独立配置的表头文本样式。
     *
     * <p>返回值属于当前 {@code TableStyle}，可直接修改。样式注册表在注册和读取时仍会复制整个
     * 表格样式，因此不会污染其他文档。</p>
     *
     * @return 表头文本样式
     */
    public RunStyle getHeaderTextStyle() {
        return headerTextStyle;
    }

    /**
     * 替换表头文本样式。
     *
     * @param headerTextStyle 非空表头文本样式
     */
    public void setHeaderTextStyle(RunStyle headerTextStyle) {
        if (headerTextStyle == null) {
            throw new IllegalArgumentException("table header text style must not be null");
        }
        this.headerTextStyle = headerTextStyle.copy();
    }

    /**
     * 获取可独立配置的表内容文本样式。
     *
     * <p>返回值属于当前 {@code TableStyle}，可直接修改。</p>
     *
     * @return 表内容文本样式
     */
    public RunStyle getBodyTextStyle() {
        return bodyTextStyle;
    }

    /**
     * 替换表内容文本样式。
     *
     * @param bodyTextStyle 非空表内容文本样式
     */
    public void setBodyTextStyle(RunStyle bodyTextStyle) {
        if (bodyTextStyle == null) {
            throw new IllegalArgumentException("table body text style must not be null");
        }
        this.bodyTextStyle = bodyTextStyle.copy();
    }

    /** 创建宋体、10.5 磅、黑色、常规字重的默认区域文本样式。 */
    private static RunStyle defaultTextStyle() {
        RunStyle style = new RunStyle();
        style.setFontFamily(DEFAULT_FONT_FAMILY);
        style.setAsciiFontFamily(DEFAULT_ASCII_FONT_FAMILY);
        style.setFarEastFontFamily(DEFAULT_FAR_EAST_FONT_FAMILY);
        style.setFontSize(DEFAULT_FONT_SIZE);
        style.setColor(DEFAULT_FONT_COLOR);
        style.setBold(false);
        style.setItalic(false);
        style.setUnderline(false);
        return style;
    }

    /** 创建中文黑体、西文新罗马、10.5 磅、黑色且不加粗的默认表头样式。 */
    private static RunStyle defaultHeaderTextStyle() {
        RunStyle style = defaultTextStyle();
        style.setFarEastFontFamily(DEFAULT_HEADER_FAR_EAST_FONT_FAMILY);
        return style;
    }
}
