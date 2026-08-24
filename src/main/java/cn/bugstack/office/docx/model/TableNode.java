package cn.bugstack.office.docx.model;

import cn.bugstack.office.docx.style.RunStyle;

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
    /** 各列相对于表格总宽度的比例权重。 */
    private double[] columnWidths = new double[0];
    /** 表格水平对齐方式。 */
    private TableHorizontalAlignment alignment = TableHorizontalAlignment.LEFT;
    /** 当前表格的可选表头文本样式覆盖。 */
    private RunStyle headerTextStyle;
    /** 当前表格的可选表内容文本样式覆盖。 */
    private RunStyle bodyTextStyle;

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
     * @return 列宽比例权重数组副本
     */
    public double[] getColumnWidths() {
        return columnWidths.clone();
    }

    /**
     * 设置表格列宽。
     *
     * @param columnWidths 列宽比例权重数组；表格最终宽度自适应当前页面正文宽度
     */
    public void setColumnWidths(double[] columnWidths) {
        this.columnWidths = columnWidths == null ? new double[0] : columnWidths.clone();
    }

    /**
     * 获取表格水平对齐方式。
     *
     * @return 水平对齐方式
     */
    public TableHorizontalAlignment getAlignment() {
        return alignment;
    }

    /**
     * 设置表格水平对齐方式。
     *
     * @param alignment 水平对齐方式；为空时恢复为左对齐
     */
    public void setAlignment(TableHorizontalAlignment alignment) {
        this.alignment = alignment == null ? TableHorizontalAlignment.LEFT : alignment;
    }

    /**
     * 获取当前表格的表头文本样式覆盖。
     *
     * @return 样式副本；未设置时为 {@code null}
     */
    public RunStyle getHeaderTextStyle() {
        return headerTextStyle == null ? null : headerTextStyle.copy();
    }

    /**
     * 设置当前表格的表头文本样式覆盖。
     *
     * @param headerTextStyle 样式；为空时继承文档表格样式
     */
    public void setHeaderTextStyle(RunStyle headerTextStyle) {
        this.headerTextStyle = headerTextStyle == null ? null : headerTextStyle.copy();
    }

    /**
     * 获取当前表格的表内容文本样式覆盖。
     *
     * @return 样式副本；未设置时为 {@code null}
     */
    public RunStyle getBodyTextStyle() {
        return bodyTextStyle == null ? null : bodyTextStyle.copy();
    }

    /**
     * 设置当前表格的表内容文本样式覆盖。
     *
     * @param bodyTextStyle 样式；为空时继承文档表格样式
     */
    public void setBodyTextStyle(RunStyle bodyTextStyle) {
        this.bodyTextStyle = bodyTextStyle == null ? null : bodyTextStyle.copy();
    }
}
