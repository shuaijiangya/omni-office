package cn.bugstack.export.document;

/**
 * 报告表格中的矩形合并区域。
 *
 * <p>行坐标包含表头，表头为第 {@code 0} 行；列坐标从 {@code 0} 开始。合并区域内除左上角外的
 * 单元格可以为空，也可以重复左上角内容；不同内容会在渲染前被校验器拒绝。</p>
 */
public final class ReportTableMerge {

    private final int startRow;
    private final int startColumn;
    private final int rowSpan;
    private final int columnSpan;

    /**
     * 创建矩形合并区域。
     *
     * @param startRow 起始行，包含表头且从 {@code 0} 开始
     * @param startColumn 起始列，从 {@code 0} 开始
     * @param rowSpan 跨行数，最小为 {@code 1}
     * @param columnSpan 跨列数，最小为 {@code 1}
     */
    public ReportTableMerge(int startRow, int startColumn, int rowSpan, int columnSpan) {
        if (startRow < 0 || startColumn < 0 || rowSpan < 1 || columnSpan < 1) {
            throw new IllegalArgumentException("invalid report table merge range");
        }
        if (rowSpan == 1 && columnSpan == 1) {
            throw new IllegalArgumentException("table merge must span multiple rows or columns");
        }
        this.startRow = startRow;
        this.startColumn = startColumn;
        this.rowSpan = rowSpan;
        this.columnSpan = columnSpan;
    }

    /** @return 起始行 */
    public int getStartRow() {
        return startRow;
    }

    /** @return 起始列 */
    public int getStartColumn() {
        return startColumn;
    }

    /** @return 跨行数 */
    public int getRowSpan() {
        return rowSpan;
    }

    /** @return 跨列数 */
    public int getColumnSpan() {
        return columnSpan;
    }

    /**
     * 判断指定逻辑单元格是否位于当前合并区域。
     *
     * @param row 逻辑行坐标
     * @param column 逻辑列坐标
     * @return 位于当前合并区域时返回 {@code true}
     */
    public boolean contains(int row, int column) {
        return row >= startRow && row < startRow + rowSpan
                && column >= startColumn && column < startColumn + columnSpan;
    }
}
