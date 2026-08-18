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
}
