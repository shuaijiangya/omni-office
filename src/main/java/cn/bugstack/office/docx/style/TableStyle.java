package cn.bugstack.office.docx.style;

/**
 * 表格样式定义。
 */
public class TableStyle {

    /** 表格样式名称。 */
    private final String name;
    /** 是否绘制表格边框。 */
    private boolean bordered;
    /** 是否加粗表头文本。 */
    private boolean headerBold;

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
        copy.headerBold = headerBold;
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
        return headerBold;
    }

    /**
     * 设置表头是否加粗。
     *
     * @param headerBold 表头是否加粗
     */
    public void setHeaderBold(boolean headerBold) {
        this.headerBold = headerBold;
    }
}
