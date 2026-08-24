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

    /** 表格列宽比例权重；表格总宽度由目标页面正文宽度自适应。 */
    private double[] columnWidths = new double[0];

    /** 表格水平对齐方式。 */
    private ReportTableAlignment alignment = ReportTableAlignment.LEFT;

    /** 表格矩形合并区域。 */
    private List<ReportTableMerge> merges = new ArrayList<>();

    /** 表格内文本的可选字体颜色。 */
    private String fontColor;

    /** 当前表格可选的表头文本样式覆盖。 */
    private ReportTextRangeStyle headerTextStyle;

    /** 当前表格可选的表内容文本样式覆盖。 */
    private ReportTextRangeStyle bodyTextStyle;

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
     * @return 列宽比例权重数组副本
     */
    public double[] getColumnWidths() {
        return columnWidths.clone();
    }

    /**
     * 设置表格列宽。
     *
     * @param columnWidths 列宽比例权重数组
     */
    public void setColumnWidths(double[] columnWidths) {
        this.columnWidths = columnWidths == null ? new double[0] : columnWidths.clone();
    }

    public ReportTableAlignment getAlignment() {
        return alignment;
    }

    public void setAlignment(ReportTableAlignment alignment) {
        this.alignment = alignment == null ? ReportTableAlignment.LEFT : alignment;
    }

    public List<ReportTableMerge> getMerges() {
        return merges;
    }

    public void setMerges(List<ReportTableMerge> merges) {
        this.merges = merges == null ? new ArrayList<>() : merges;
    }

    public String getFontColor() {
        return fontColor;
    }

    public void setFontColor(String fontColor) {
        this.fontColor = fontColor;
    }

    /** @return 当前表格的表头文本样式覆盖 */
    public ReportTextRangeStyle getHeaderTextStyle() {
        return headerTextStyle;
    }

    /** @param headerTextStyle 表头文本样式覆盖 */
    public void setHeaderTextStyle(ReportTextRangeStyle headerTextStyle) {
        this.headerTextStyle = headerTextStyle;
    }

    /** @return 当前表格的表内容文本样式覆盖 */
    public ReportTextRangeStyle getBodyTextStyle() {
        return bodyTextStyle;
    }

    /** @param bodyTextStyle 表内容文本样式覆盖 */
    public void setBodyTextStyle(ReportTextRangeStyle bodyTextStyle) {
        this.bodyTextStyle = bodyTextStyle;
    }
}
