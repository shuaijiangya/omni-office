package cn.bugstack.application.completion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 由 AI 或普通业务代码生成、等待本地校验与执行的表格更新计划。 */
public final class TablePatchPlan {

    private final String tableId;
    private final TableOverwritePolicy overwritePolicy;
    private final List<Map<String, String>> rows;

    /**
     * 创建 UPSERT 行计划。
     *
     * @param tableId 目标表格标识
     * @param overwritePolicy 已有单元格覆盖策略
     * @param rows 使用列标识作为键的数据行
     */
    public TablePatchPlan(String tableId, TableOverwritePolicy overwritePolicy,
                          List<Map<String, String>> rows) {
        this.tableId = tableId;
        this.overwritePolicy = overwritePolicy;
        List<Map<String, String>> copy = new ArrayList<>();
        for (Map<String, String> row : rows) {
            copy.add(Collections.unmodifiableMap(new LinkedHashMap<>(row)));
        }
        this.rows = Collections.unmodifiableList(copy);
    }

    /** @return 目标表格标识 */
    public String getTableId() {
        return tableId;
    }

    /** @return 已有单元格覆盖策略 */
    public TableOverwritePolicy getOverwritePolicy() {
        return overwritePolicy;
    }

    /** @return 不可变数据行 */
    public List<Map<String, String>> getRows() {
        return rows;
    }
}
