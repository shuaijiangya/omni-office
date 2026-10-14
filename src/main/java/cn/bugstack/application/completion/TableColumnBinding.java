package cn.bugstack.application.completion;

/** 表格业务字段与 Word 逻辑列之间的绑定。 */
public final class TableColumnBinding {

    private final String columnId;
    private final String header;
    private final int columnIndex;

    /**
     * 创建列绑定。
     *
     * @param columnId 供 AI 和补丁计划引用的稳定列标识
     * @param header 当前模板表头
     * @param columnIndex 从 0 开始的逻辑列序号
     */
    public TableColumnBinding(String columnId, String header, int columnIndex) {
        this.columnId = columnId;
        this.header = header;
        this.columnIndex = columnIndex;
    }

    /** @return 稳定列标识 */
    public String getColumnId() {
        return columnId;
    }

    /** @return 当前模板表头 */
    public String getHeader() {
        return header;
    }

    /** @return 从 0 开始的逻辑列序号 */
    public int getColumnIndex() {
        return columnIndex;
    }
}
