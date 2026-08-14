package cn.bugstack.office.docx.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 表格块级节点，组合多个表格行。
 */
public class TableNode implements DocxBlock {

    private final List<TableRowNode> rows = new ArrayList<>();
    /** 表格样式名称。 */
    private String styleName = "TableNormal";
    /** 各列的目标宽度，单位为磅。 */
    private double[] columnWidths = new double[0];

    /**
     * 创建空的表格节点。
     */
    public TableNode() {
    }

    /**
     * 追加表格行。
     *
     * @param row 表格行节点
     */
    public void addRow(TableRowNode row) {
        rows.add(row);
    }

    /**
     * 获取表格行列表。
     *
     * @return 不可修改的表格行列表
     */
    public List<TableRowNode> getRows() {
        return Collections.unmodifiableList(rows);
    }

    /**
     * 获取表格样式名称。
     *
     * @return 表格样式名称
     */
    public String getStyleName() {
        return styleName;
    }

    /**
     * 设置表格样式名称。
     *
     * @param styleName 表格样式名称
     */
    public void setStyleName(String styleName) {
        this.styleName = styleName;
    }

    /**
     * 获取表格列宽。
     *
     * @return 列宽数组副本，单位为 point
     */
    public double[] getColumnWidths() {
        return columnWidths.clone();
    }

    /**
     * 设置表格列宽。
     *
     * @param columnWidths 列宽数组，单位为 point
     */
    public void setColumnWidths(double[] columnWidths) {
        this.columnWidths = columnWidths == null ? new double[0] : columnWidths.clone();
    }
}
