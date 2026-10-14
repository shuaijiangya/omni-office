package cn.bugstack.application.completion;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 在写入 Word 前验证表格绑定、行主键和补丁数据。 */
public final class TablePatchValidator {

    /**
     * 验证表格补丁。
     *
     * @param table 当前源文档表格快照
     * @param binding 已确认绑定
     * @param plan 更新计划
     * @return 校验错误；空列表表示可执行
     */
    public List<String> validate(SourceTableSnapshot table, TableBindingSpec binding, TablePatchPlan plan) {
        List<String> errors = new ArrayList<>();
        if (table == null || binding == null || plan == null) {
            errors.add("table snapshot, binding and patch plan are required");
            return errors;
        }
        if (!table.getTableId().equals(binding.getTableId())
                || !table.getTableId().equals(plan.getTableId())) {
            errors.add("table id does not match binding or patch plan");
        }
        if (!table.getFingerprint().equals(binding.getTableFingerprint())) {
            errors.add("table structure fingerprint changed; analyze the source document again");
        }
        if (table.isMergedCells()) {
            errors.add("table row patch does not support merged source cells");
        }
        if (table.isNestedTables()) {
            errors.add("table row patch does not support nested source tables");
        }
        if (table.isComplexContent()) {
            errors.add("table contains objects, fields or multiple paragraphs; plain-text patch would lose content");
        }
        for (int index = 0; index < table.getRows().size(); index++) {
            if (table.getRows().get(index).size() != table.getHeaders().size()) {
                errors.add("source table data row " + index + " does not match the header width");
            }
        }
        if (binding.getDataStartRow() < 1) {
            errors.add("dataStartRow must preserve the header row");
        }
        if (binding.getTemplateRowIndex() < binding.getDataStartRow()
                || binding.getTemplateRowIndex() > table.getRows().size()) {
            errors.add("template row must identify an existing data row");
        }
        Map<String, TableColumnBinding> columns = validateColumns(table, binding, errors);
        if (binding.getRowKeyColumnIds().isEmpty()) {
            errors.add("at least one row key column is required for UPSERT");
        }
        if (plan.getOverwritePolicy() == null) {
            errors.add("table overwrite policy is required");
        }
        if (plan.getRows().isEmpty()) {
            errors.add("table patch must contain at least one row");
        }
        for (String key : binding.getRowKeyColumnIds()) {
            if (!columns.containsKey(key)) {
                errors.add("unknown row key column: " + key);
            }
        }
        Set<String> plannedKeys = new HashSet<>();
        for (int index = 0; index < plan.getRows().size(); index++) {
            Map<String, String> row = plan.getRows().get(index);
            for (String columnId : row.keySet()) {
                if (!columns.containsKey(columnId)) {
                    errors.add("unknown patch column at row " + index + ": " + columnId);
                }
            }
            String key = rowKey(row, binding.getRowKeyColumnIds());
            if (key == null) {
                errors.add("patch row " + index + " is missing a non-blank row key");
            } else if (!plannedKeys.add(key)) {
                errors.add("duplicate patch row key at row " + index + ": " + key);
            }
        }
        validateExistingKeys(table, binding, columns, errors);
        return errors;
    }

    private Map<String, TableColumnBinding> validateColumns(SourceTableSnapshot table,
                                                            TableBindingSpec binding,
                                                            List<String> errors) {
        Map<String, TableColumnBinding> columns = new HashMap<>();
        Set<Integer> indexes = new HashSet<>();
        for (TableColumnBinding column : binding.getColumns()) {
            if (column.getColumnId() == null || column.getColumnId().trim().isEmpty()) {
                errors.add("column id must not be blank");
                continue;
            }
            if (columns.put(column.getColumnId(), column) != null) {
                errors.add("duplicate column id: " + column.getColumnId());
            }
            if (!indexes.add(column.getColumnIndex())) {
                errors.add("duplicate column index: " + column.getColumnIndex());
            }
            if (column.getColumnIndex() < 0 || column.getColumnIndex() >= table.getHeaders().size()) {
                errors.add("column index is outside the source table: " + column.getColumnId());
            } else if (!table.getHeaders().get(column.getColumnIndex()).equals(column.getHeader())) {
                errors.add("column header changed for " + column.getColumnId());
            }
        }
        return columns;
    }

    private void validateExistingKeys(SourceTableSnapshot table, TableBindingSpec binding,
                                      Map<String, TableColumnBinding> columns, List<String> errors) {
        Set<String> keys = new HashSet<>();
        for (int index = 0; index < table.getRows().size(); index++) {
            List<String> values = table.getRows().get(index);
            Map<String, String> row = new HashMap<>();
            for (TableColumnBinding column : columns.values()) {
                if (column.getColumnIndex() < values.size()) {
                    row.put(column.getColumnId(), values.get(column.getColumnIndex()));
                }
            }
            String key = rowKey(row, binding.getRowKeyColumnIds());
            if (key != null && !keys.add(key)) {
                errors.add("source table contains duplicate row key at data row " + index + ": " + key);
            }
        }
    }

    static String rowKey(Map<String, String> row, List<String> keyColumns) {
        if (keyColumns.isEmpty()) {
            return null;
        }
        StringBuilder result = new StringBuilder();
        for (String column : keyColumns) {
            String value = row.get(column);
            if (value == null || value.trim().isEmpty()) {
                return null;
            }
            if (result.length() > 0) {
                result.append('\u001F');
            }
            result.append(value.trim());
        }
        return result.toString();
    }
}
