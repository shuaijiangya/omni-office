package cn.bugstack.application.completion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 表格稳定定位、列映射和行主键定义。
 *
 * <p>该对象可以由 AI 建议，但必须由本地校验器确认后才能执行。</p>
 */
public final class TableBindingSpec {

    private final String bindingId;
    private final String tableId;
    private final String tableFingerprint;
    private final String sourceSha256;
    private final int dataStartRow;
    private final int templateRowIndex;
    private final List<TableColumnBinding> columns;
    private final List<String> rowKeyColumnIds;

    /** 创建表格绑定。 */
    public TableBindingSpec(String bindingId, String tableId, String tableFingerprint,
                            String sourceSha256,
                            int dataStartRow, int templateRowIndex,
                            List<TableColumnBinding> columns, List<String> rowKeyColumnIds) {
        this.bindingId = bindingId;
        this.tableId = tableId;
        this.tableFingerprint = tableFingerprint;
        this.sourceSha256 = sourceSha256;
        this.dataStartRow = dataStartRow;
        this.templateRowIndex = templateRowIndex;
        this.columns = Collections.unmodifiableList(new ArrayList<>(columns));
        this.rowKeyColumnIds = Collections.unmodifiableList(new ArrayList<>(rowKeyColumnIds));
    }

    /** @return 绑定标识 */
    public String getBindingId() {
        return bindingId;
    }

    /** @return 表格标识 */
    public String getTableId() {
        return tableId;
    }

    /** @return 生成绑定时的表格结构指纹 */
    public String getTableFingerprint() {
        return tableFingerprint;
    }

    /** @return 制定绑定时的源文件摘要 */
    public String getSourceSha256() {
        return sourceSha256;
    }

    /** @return 第一条数据所在的 Word 物理行序号 */
    public int getDataStartRow() {
        return dataStartRow;
    }

    /** @return 新增数据时克隆的 Word 物理行序号 */
    public int getTemplateRowIndex() {
        return templateRowIndex;
    }

    /** @return 列绑定 */
    public List<TableColumnBinding> getColumns() {
        return columns;
    }

    /** @return 用于匹配已有行的列标识 */
    public List<String> getRowKeyColumnIds() {
        return rowKeyColumnIds;
    }

    /**
     * 使用指定行主键创建新绑定。
     *
     * @param keyColumnIds 行主键列标识
     * @return 新绑定
     */
    public TableBindingSpec withRowKeyColumnIds(List<String> keyColumnIds) {
        return new TableBindingSpec(bindingId, tableId, tableFingerprint, sourceSha256, dataStartRow,
                templateRowIndex, columns, keyColumnIds);
    }
}
