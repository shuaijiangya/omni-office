package cn.bugstack.protocol.document.block;

import java.util.ArrayList;
import java.util.List;

/**
 * 纯文本单元格表格。
 */
public final class TableBlockSpec extends BlockSpec {

    private List<String> headers = new ArrayList<>();
    private List<List<String>> rows = new ArrayList<>();
    private List<Double> columnWidths = new ArrayList<>();
    private String styleName;
    private String caption;
    private boolean captionAutoNumbered = true;
    private String captionPosition = "BELOW";
    private String alignment = "LEFT";
    private List<TableMergeSpec> merges = new ArrayList<>();
    private String fontColor;
    private TextRangeStyleSpec headerTextStyle;
    private TextRangeStyleSpec bodyTextStyle;

    public List<String> getHeaders() {
        return headers;
    }

    public void setHeaders(List<String> headers) {
        this.headers = headers == null ? new ArrayList<>() : headers;
    }

    public List<List<String>> getRows() {
        return rows;
    }

    public void setRows(List<List<String>> rows) {
        this.rows = rows == null ? new ArrayList<>() : rows;
    }

    public List<Double> getColumnWidths() {
        return columnWidths;
    }

    public void setColumnWidths(List<Double> columnWidths) {
        this.columnWidths = columnWidths == null ? new ArrayList<>() : columnWidths;
    }

    public String getStyleName() {
        return styleName;
    }

    public void setStyleName(String styleName) {
        this.styleName = styleName;
    }

    public String getCaption() {
        return caption;
    }

    public void setCaption(String caption) {
        this.caption = caption;
    }

    public boolean isCaptionAutoNumbered() {
        return captionAutoNumbered;
    }

    public void setCaptionAutoNumbered(boolean captionAutoNumbered) {
        this.captionAutoNumbered = captionAutoNumbered;
    }

    public String getCaptionPosition() {
        return captionPosition;
    }

    public void setCaptionPosition(String captionPosition) {
        this.captionPosition = captionPosition;
    }

    public String getAlignment() {
        return alignment;
    }

    public void setAlignment(String alignment) {
        this.alignment = alignment;
    }

    public List<TableMergeSpec> getMerges() {
        return merges;
    }

    public void setMerges(List<TableMergeSpec> merges) {
        this.merges = merges == null ? new ArrayList<>() : merges;
    }

    public String getFontColor() {
        return fontColor;
    }

    public void setFontColor(String fontColor) {
        this.fontColor = fontColor;
    }

    /** @return 当前表格的表头文本样式覆盖 */
    public TextRangeStyleSpec getHeaderTextStyle() {
        return headerTextStyle;
    }

    /** @param headerTextStyle 表头文本样式覆盖 */
    public void setHeaderTextStyle(TextRangeStyleSpec headerTextStyle) {
        this.headerTextStyle = headerTextStyle;
    }

    /** @return 当前表格的表内容文本样式覆盖 */
    public TextRangeStyleSpec getBodyTextStyle() {
        return bodyTextStyle;
    }

    /** @param bodyTextStyle 表内容文本样式覆盖 */
    public void setBodyTextStyle(TextRangeStyleSpec bodyTextStyle) {
        this.bodyTextStyle = bodyTextStyle;
    }
}
