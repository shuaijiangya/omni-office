package cn.bugstack.application.completion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 上传 DOCX 中单个表格的只读逻辑快照。 */
public final class SourceTableSnapshot {

    private final String tableId;
    private final String fingerprint;
    private final int tableIndex;
    private final int sectionIndex;
    private final String caption;
    private final List<String> headers;
    private final List<List<String>> rows;
    private final boolean mergedCells;
    private final boolean nestedTables;
    private final boolean complexContent;
    private final List<List<Boolean>> writableCells;

    /**
     * 创建表格快照。
     *
     * @param tableId 稳定表格标识
     * @param fingerprint 表格结构指纹
     * @param tableIndex 文档中的全局表格序号
     * @param sectionIndex 所属 Section 序号
     * @param caption 邻近题注或说明
     * @param headers 第一行表头
     * @param rows 数据行，不包含表头
     * @param mergedCells 是否包含合并单元格
     * @param nestedTables 是否包含嵌套表格
     * @param complexContent 是否包含可能在纯文本写回时丢失的对象或多段落
     * @param writableCells 按物理行列排列的可安全写入单元格标志
     */
    public SourceTableSnapshot(String tableId, String fingerprint, int tableIndex, int sectionIndex,
                               String caption, List<String> headers, List<List<String>> rows,
                               boolean mergedCells, boolean nestedTables, boolean complexContent,
                               List<List<Boolean>> writableCells) {
        this.tableId = tableId;
        this.fingerprint = fingerprint;
        this.tableIndex = tableIndex;
        this.sectionIndex = sectionIndex;
        this.caption = caption;
        this.headers = Collections.unmodifiableList(new ArrayList<>(headers));
        List<List<String>> copy = new ArrayList<>();
        for (List<String> row : rows) {
            copy.add(Collections.unmodifiableList(new ArrayList<>(row)));
        }
        this.rows = Collections.unmodifiableList(copy);
        this.mergedCells = mergedCells;
        this.nestedTables = nestedTables;
        this.complexContent = complexContent;
        List<List<Boolean>> writableCopy = new ArrayList<>();
        for (List<Boolean> row : writableCells) {
            writableCopy.add(Collections.unmodifiableList(new ArrayList<>(row)));
        }
        this.writableCells = Collections.unmodifiableList(writableCopy);
    }

    /** @return 表格标识 */
    public String getTableId() {
        return tableId;
    }

    /** @return 表格结构指纹 */
    public String getFingerprint() {
        return fingerprint;
    }

    /** @return 文档中的全局表格序号 */
    public int getTableIndex() {
        return tableIndex;
    }

    /** @return 所属 Section 序号 */
    public int getSectionIndex() {
        return sectionIndex;
    }

    /** @return 邻近题注或说明 */
    public String getCaption() {
        return caption;
    }

    /** @return 第一行表头 */
    public List<String> getHeaders() {
        return headers;
    }

    /** @return 数据行，不包含表头 */
    public List<List<String>> getRows() {
        return rows;
    }

    /** @return 是否包含合并单元格 */
    public boolean isMergedCells() {
        return mergedCells;
    }

    /** @return 是否包含嵌套表格 */
    public boolean isNestedTables() {
        return nestedTables;
    }

    /** @return 表格中是否含图片、字段、多段落等不能直接用纯文本替换的内容 */
    public boolean isComplexContent() { return complexContent; }

    /** @return 各物理单元格是否可在不删除现有对象的情况下替换纯文本 */
    public List<List<Boolean>> getWritableCells() { return writableCells; }

    /** @return 指定物理单元格是否可安全写入 */
    public boolean isCellWritable(int rowIndex, int columnIndex) {
        return rowIndex >= 0 && rowIndex < writableCells.size()
                && columnIndex >= 0 && columnIndex < writableCells.get(rowIndex).size()
                && Boolean.TRUE.equals(writableCells.get(rowIndex).get(columnIndex));
    }
}
