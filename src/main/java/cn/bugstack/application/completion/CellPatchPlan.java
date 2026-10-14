package cn.bugstack.application.completion;

/** 固定格式表格中一个物理单元格的精确文本补全计划。 */
public final class CellPatchPlan {
    private final String tableId;
    private final int rowIndex;
    private final int columnIndex;
    private final TableOverwritePolicy overwritePolicy;
    private final String text;

    /** 创建以零为起点的物理行列定位补丁。 */
    public CellPatchPlan(String tableId, int rowIndex, int columnIndex,
                         TableOverwritePolicy overwritePolicy, String text) {
        this.tableId = tableId;
        this.rowIndex = rowIndex;
        this.columnIndex = columnIndex;
        this.overwritePolicy = overwritePolicy;
        this.text = text;
    }

    /** @return 稳定表格 ID */
    public String getTableId() { return tableId; }

    /** @return 从零开始的物理行序号 */
    public int getRowIndex() { return rowIndex; }

    /** @return 从零开始的物理列序号 */
    public int getColumnIndex() { return columnIndex; }

    /** @return 覆盖策略 */
    public TableOverwritePolicy getOverwritePolicy() { return overwritePolicy; }

    /** @return 写入文本 */
    public String getText() { return text; }
}
