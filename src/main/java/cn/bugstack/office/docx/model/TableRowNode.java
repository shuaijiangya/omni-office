package cn.bugstack.office.docx.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 表格行节点，组合多个单元格。
 */
public class TableRowNode implements DocxNode {

    private final List<TableCellNode> cells = new ArrayList<>();

    /**
     * 创建空的表格行节点。
     */
    public TableRowNode() {
    }

    /**
     * 追加单元格。
     *
     * @param cell 单元格节点
     */
    public void addCell(TableCellNode cell) {
        cells.add(cell);
    }

    /**
     * 获取单元格列表。
     *
     * @return 不可修改的单元格列表
     */
    public List<TableCellNode> getCells() {
        return Collections.unmodifiableList(cells);
    }
}
