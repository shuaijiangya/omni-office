package cn.bugstack.export.document;

import java.util.ArrayList;
import java.util.List;

/**
 * 报告中的表格内容。
 */
public class ReportTable implements ReportElement {

    /** 表头列名称。 */
    private List<String> headers = new ArrayList<>();

    /** 表格数据行。 */
    private List<List<String>> rows = new ArrayList<>();

    /** 表格题注。 */
    private ReportCaption caption;

    /** 表格样式名称。 */
    private String styleName;

    /** 表格列宽，单位由目标格式编译器解释。 */
    private double[] columnWidths = new double[0];

    /**
     * 获取当前元素的类型。
     *
     * @return 表格元素类型
     */
    @Override
    public ReportElementType getElementType() {
        return ReportElementType.TABLE;
    }

    /**
     * 获取表头列名称。
     *
     * @return 表头列名称
     */
    public List<String> getHeaders() {
        return headers;
    }

    /**
     * 设置表头列名称。
     *
     * @param headers 表头列名称
     */
    public void setHeaders(List<String> headers) {
        this.headers = headers == null ? new ArrayList<>() : headers;
    }

    /**
     * 获取表格数据行。
     *
     * @return 表格数据行
     */
    public List<List<String>> getRows() {
        return rows;
    }

    /**
     * 设置表格数据行。
     *
     * @param rows 表格数据行
     */
    public void setRows(List<List<String>> rows) {
        this.rows = rows == null ? new ArrayList<>() : rows;
    }

    /**
     * 获取表格题注。
     *
     * @return 表格题注
     */
    public ReportCaption getCaption() {
        return caption;
    }

    /**
     * 设置表格题注。
     *
     * @param caption 表格题注
     */
    public void setCaption(ReportCaption caption) {
        this.caption = caption;
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
     * @return 列宽数组副本
     */
    public double[] getColumnWidths() {
        return columnWidths.clone();
    }

    /**
     * 设置表格列宽。
     *
     * @param columnWidths 列宽数组
     */
    public void setColumnWidths(double[] columnWidths) {
        this.columnWidths = columnWidths == null ? new double[0] : columnWidths.clone();
    }
}
