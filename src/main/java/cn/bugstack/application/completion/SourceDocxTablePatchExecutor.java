package cn.bugstack.application.completion;

import cn.bugstack.office.docx.render.AsposeWordsLicenseLoader;
import com.aspose.words.Cell;
import com.aspose.words.Document;
import com.aspose.words.NodeCollection;
import com.aspose.words.NodeType;
import com.aspose.words.Paragraph;
import com.aspose.words.Row;
import com.aspose.words.Run;
import com.aspose.words.SaveFormat;
import com.aspose.words.Table;

import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 使用已校验的绑定和补丁计划确定性修改源 DOCX 表格。
 *
 * <p>行 UPSERT 只处理无合并、无嵌套表格的纯文本重复行，并通过克隆模板行保留原有样式。</p>
 */
public final class SourceDocxTablePatchExecutor {

    private final SourceDocxStructureExtractor extractor;
    private final TablePatchValidator validator;

    /** 创建默认 DOCX 表格补丁执行器。 */
    public SourceDocxTablePatchExecutor() {
        this(new SourceDocxStructureExtractor(), new TablePatchValidator());
    }

    /** 创建使用指定依赖的执行器。 */
    public SourceDocxTablePatchExecutor(SourceDocxStructureExtractor extractor,
                                        TablePatchValidator validator) {
        if (extractor == null || validator == null) {
            throw new IllegalArgumentException("DOCX extractor and table patch validator are required");
        }
        this.extractor = extractor;
        this.validator = validator;
    }

    /**
     * 应用 UPSERT 行计划并原子保存新 DOCX。
     *
     * @param source 源 DOCX
     * @param output 输出 DOCX，可以与源路径相同
     * @param binding 已确认绑定
     * @param plan 已生成的补丁计划
     * @return 更新与新增统计
     */
    public TablePatchResult apply(Path source, Path output, TableBindingSpec binding,
                                  TablePatchPlan plan) {
        if (output == null) {
            throw new IllegalArgumentException("output DOCX path is required");
        }
        if (binding == null || plan == null) {
            throw new IllegalArgumentException("table binding and patch plan are required");
        }
        DocumentStructureSnapshot snapshot = extractor.extract(source);
        if (!snapshot.getSha256().equals(binding.getSourceSha256())) {
            throw new IllegalArgumentException("source DOCX changed after planning; analyze it again");
        }
        SourceTableSnapshot tableSnapshot = findTable(snapshot, plan.getTableId());
        List<String> errors = validator.validate(tableSnapshot, binding, plan);
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException("table patch validation failed: " + String.join("; ", errors));
        }
        try {
            AsposeWordsLicenseLoader.applyConfiguredLicense();
            Document document = new Document(source.toString());
            int[] counts = applyToDocument(document, tableSnapshot, binding, plan);
            atomicSave(document, output);
            return new TablePatchResult(output.toAbsolutePath().normalize(), counts[0], counts[1]);
        } catch (Exception e) {
            throw new IllegalStateException("failed to apply table patch to source DOCX", e);
        }
    }

    /** 在已加载文档中执行补丁，不保存；供文档级多位置补全复用。 */
    int[] applyToDocument(Document document, SourceTableSnapshot tableSnapshot,
                          TableBindingSpec binding, TablePatchPlan plan) {
        NodeCollection tables = document.getChildNodes(NodeType.TABLE, true);
        Table table = (Table) tables.get(tableSnapshot.getTableIndex());
        Map<String, TableColumnBinding> columns = columns(binding);
        Map<String, Row> existing = existingRows(table, binding, columns);
        int updated = 0;
        int appended = 0;
        for (Map<String, String> values : plan.getRows()) {
            String key = TablePatchValidator.rowKey(values, binding.getRowKeyColumnIds());
            Row row = existing.get(key);
            if (row == null) {
                row = appendTemplateRow(document, table, binding.getTemplateRowIndex());
                clearBoundCells(document, row, binding.getColumns());
                writeValues(document, row, columns, values, TableOverwritePolicy.OVERWRITE_ALL);
                existing.put(key, row);
                appended++;
            } else if (writeValues(document, row, columns, values, plan.getOverwritePolicy())) {
                updated++;
            }
        }
        return new int[]{updated, appended};
    }

    /** 在校验后按源文档的物理行列写入单个固定格式表格单元格。 */
    boolean applyCellToDocument(Document document, SourceTableSnapshot tableSnapshot, CellPatchPlan plan) {
        NodeCollection tables = document.getChildNodes(NodeType.TABLE, true);
        Table table = (Table) tables.get(tableSnapshot.getTableIndex());
        Cell cell = table.getRows().get(plan.getRowIndex()).getCells().get(plan.getColumnIndex());
        String previous = cellText(cell);
        String replacement = plan.getText().trim();
        if (previous.equals(replacement)) return false;
        setCellText(document, cell, replacement);
        return true;
    }

    private SourceTableSnapshot findTable(DocumentStructureSnapshot snapshot, String tableId) {
        for (SourceTableSnapshot table : snapshot.getTables()) {
            if (table.getTableId().equals(tableId)) {
                return table;
            }
        }
        throw new IllegalArgumentException("source DOCX does not contain table: " + tableId);
    }

    private Map<String, TableColumnBinding> columns(TableBindingSpec binding) {
        Map<String, TableColumnBinding> columns = new LinkedHashMap<>();
        for (TableColumnBinding column : binding.getColumns()) {
            columns.put(column.getColumnId(), column);
        }
        return columns;
    }

    private Map<String, Row> existingRows(Table table, TableBindingSpec binding,
                                          Map<String, TableColumnBinding> columns) {
        Map<String, Row> rows = new HashMap<>();
        for (int index = binding.getDataStartRow(); index < table.getRows().getCount(); index++) {
            Row row = table.getRows().get(index);
            Map<String, String> values = new HashMap<>();
            for (TableColumnBinding column : columns.values()) {
                values.put(column.getColumnId(), cellText(row, column.getColumnIndex()));
            }
            String key = TablePatchValidator.rowKey(values, binding.getRowKeyColumnIds());
            if (key != null) {
                rows.put(key, row);
            }
        }
        return rows;
    }

    private Row appendTemplateRow(Document document, Table table, int templateRowIndex) {
        Row template = table.getRows().get(templateRowIndex);
        Row clone = (Row) template.deepClone(true);
        table.appendChild(clone);
        return clone;
    }

    private void clearBoundCells(Document document, Row row, List<TableColumnBinding> columns) {
        for (TableColumnBinding column : columns) {
            setCellText(document, row.getCells().get(column.getColumnIndex()), "");
        }
    }

    private boolean writeValues(Document document, Row row, Map<String, TableColumnBinding> columns,
                                Map<String, String> values, TableOverwritePolicy policy) {
        boolean changed = false;
        for (Map.Entry<String, String> value : values.entrySet()) {
            TableColumnBinding column = columns.get(value.getKey());
            if (column == null) {
                continue;
            }
            Cell cell = row.getCells().get(column.getColumnIndex());
            if (policy == TableOverwritePolicy.BLANKS_ONLY && !cellText(cell).isEmpty()) {
                continue;
            }
            String target = value.getValue() == null ? "" : value.getValue();
            if (!cellText(cell).equals(target.trim())) {
                setCellText(document, cell, target);
                changed = true;
            }
        }
        return changed;
    }

    private String cellText(Row row, int columnIndex) {
        return cellText(row.getCells().get(columnIndex));
    }

    private String cellText(Cell cell) {
        return cell.getText().replace("\u0007", "").replace('\r', ' ')
                .replace('\n', ' ').trim().replaceAll("\\s+", " ");
    }

    private void setCellText(Document document, Cell cell, String text) {
        Paragraph paragraph = cell.getFirstParagraph();
        Run prototype = paragraph != null && paragraph.getRuns().getCount() > 0
                ? (Run) paragraph.getRuns().get(0).deepClone(true) : new Run(document);
        if (paragraph == null) {
            paragraph = new Paragraph(document);
            cell.appendChild(paragraph);
        }
        while (cell.getParagraphs().getCount() > 1) {
            cell.getLastParagraph().remove();
        }
        paragraph.removeAllChildren();
        prototype.setText(text == null ? "" : text);
        paragraph.appendChild(prototype);
    }

    static void atomicSave(Document document, Path output) throws Exception {
        Path absolute = output.toAbsolutePath().normalize();
        Path parent = absolute.getParent();
        if (parent == null) {
            throw new IllegalArgumentException("output DOCX must have a parent directory");
        }
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, ".omni-completion-", ".docx");
        try {
            document.save(temporary.toString(), SaveFormat.DOCX);
            try {
                Files.move(temporary, absolute, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, absolute, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
