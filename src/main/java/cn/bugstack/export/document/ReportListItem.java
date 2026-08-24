package cn.bugstack.export.document;

/**
 * 报告列表项。
 */
public class ReportListItem implements ReportElement {

    /** 列表项类型。 */
    private final ReportListType listType;
    /** 列表项文本。 */
    private final String text;
    /** 列表项段落样式名称。 */
    private String styleName;
    /** 可选字体颜色，格式为 {@code #RRGGBB}。 */
    private String fontColor;

    /**
     * 创建列表项。
     *
     * @param listType 列表类型
     * @param text 列表项文本
     */
    public ReportListItem(ReportListType listType, String text) {
        if (listType == null) {
            throw new IllegalArgumentException("report list type must not be null");
        }
        this.listType = listType;
        this.text = text;
    }

    /**
     * 获取当前元素的语义类型。
     *
     * @return 列表项类型
     */
    @Override
    public ReportElementType getElementType() {
        return ReportElementType.LIST_ITEM;
    }

    /**
     * 获取列表展示类型。
     *
     * @return 项目符号或编号列表类型
     */
    public ReportListType getListType() {
        return listType;
    }

    /**
     * 获取列表项文本。
     *
     * @return 列表项文本
     */
    public String getText() {
        return text;
    }

    /**
     * 获取可选的段落样式名称。
     *
     * @return 样式名称；未设置时为 {@code null}
     */
    public String getStyleName() {
        return styleName;
    }

    /**
     * 设置渲染列表项时使用的段落样式。
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
}
