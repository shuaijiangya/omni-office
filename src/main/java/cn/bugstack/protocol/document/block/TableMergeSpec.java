package cn.bugstack.protocol.document.block;

/**
 * DocumentSpec 表格中的矩形合并区域。
 *
 * <p>{@code startRow} 包含表头行，表头为第 {@code 0} 行；所有坐标均从 {@code 0} 开始。
 * 合并区域内除左上角外的单元格可以为空，也可以重复左上角内容；不同内容会被拒绝，避免内容被静默丢弃。</p>
 */
public final class TableMergeSpec {

    private int startRow;
    private int startColumn;
    private int rowSpan = 1;
    private int columnSpan = 1;

    /** 创建空合并配置，供 JSON 反序列化使用。 */
    public TableMergeSpec() {
    }

    /**
     * 创建指定矩形合并区域。
     *
     * @param startRow 起始行
     * @param startColumn 起始列
     * @param rowSpan 跨行数
     * @param columnSpan 跨列数
     */
    public TableMergeSpec(int startRow, int startColumn, int rowSpan, int columnSpan) {
        this.startRow = startRow;
        this.startColumn = startColumn;
        this.rowSpan = rowSpan;
        this.columnSpan = columnSpan;
    }

    /** @return 起始行 */
    public int getStartRow() {
        return startRow;
    }

    /** @param startRow 起始行 */
    public void setStartRow(int startRow) {
        this.startRow = startRow;
    }

    /** @return 起始列 */
    public int getStartColumn() {
        return startColumn;
    }

    /** @param startColumn 起始列 */
    public void setStartColumn(int startColumn) {
        this.startColumn = startColumn;
    }

    /** @return 跨行数 */
    public int getRowSpan() {
        return rowSpan;
    }

    /** @param rowSpan 跨行数 */
    public void setRowSpan(int rowSpan) {
        this.rowSpan = rowSpan;
    }

    /** @return 跨列数 */
    public int getColumnSpan() {
        return columnSpan;
    }

    /** @param columnSpan 跨列数 */
    public void setColumnSpan(int columnSpan) {
        this.columnSpan = columnSpan;
    }
}
