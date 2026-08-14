package cn.bugstack.export.context;

import java.util.ArrayList;
import java.util.List;

/**
 * 导出前的表格数据。
 */
public class ReportTableData {

    /** 表头列名称。 */
    private List<String> headers = new ArrayList<>();

    /** 表格数据行。 */
    private List<List<String>> rows = new ArrayList<>();

    /** 表格题注正文。 */
    private String caption;

    /** 是否自动编号表格题注。 */
    private boolean captionAutoNumbered = true;

    /** 表格样式名称。 */
    private String styleName;

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
     * 获取表格题注正文。
     *
     * @return 表格题注正文
     */
    public String getCaption() {
        return caption;
    }

    /**
     * 设置表格题注正文。
     *
     * @param caption 表格题注正文
     */
    public void setCaption(String caption) {
        this.caption = caption;
    }

    /**
     * 判断是否自动编号表格题注。
     *
     * @return {@code true} 表示自动编号
     */
    public boolean isCaptionAutoNumbered() {
        return captionAutoNumbered;
    }

    /**
     * 设置是否自动编号表格题注。
     *
     * @param captionAutoNumbered 是否自动编号
     */
    public void setCaptionAutoNumbered(boolean captionAutoNumbered) {
        this.captionAutoNumbered = captionAutoNumbered;
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
}
