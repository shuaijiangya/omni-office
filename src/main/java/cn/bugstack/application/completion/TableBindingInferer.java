package cn.bugstack.application.completion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 根据提取的表头生成可供 AI 引用的初始列绑定。 */
public final class TableBindingInferer {

    /**
     * 为普通首行表头表格生成初始绑定。
     *
     * @param table 表格快照
     * @return 尚未指定行主键的初始绑定
     */
    public TableBindingSpec infer(SourceTableSnapshot table, String sourceSha256) {
        if (table == null || table.getHeaders().isEmpty()) {
            throw new IllegalArgumentException("table snapshot and headers are required");
        }
        if (sourceSha256 == null || sourceSha256.isEmpty()) {
            throw new IllegalArgumentException("source document SHA-256 is required");
        }
        List<TableColumnBinding> columns = new ArrayList<>();
        for (int index = 0; index < table.getHeaders().size(); index++) {
            columns.add(new TableColumnBinding("column_" + index, table.getHeaders().get(index), index));
        }
        int templateRow = table.getRows().isEmpty() ? -1 : 1;
        return new TableBindingSpec("binding-" + table.getTableId(), table.getTableId(),
                table.getFingerprint(), sourceSha256, 1, templateRow, columns, Collections.emptyList());
    }
}
