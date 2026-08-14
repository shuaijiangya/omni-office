package cn.bugstack.export.document;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 报告语义表格构建器。
 */
public final class ReportTableBuilder {

    /** 所属章节构建器。 */
    private final ReportSectionBuilder parent;
    private final ReportTable table = new ReportTable();

    ReportTableBuilder(ReportSectionBuilder parent, String... headers) {
        if (parent == null) {
            throw new IllegalArgumentException("report table parent must not be null");
        }
        if (headers == null || headers.length == 0) {
            throw new IllegalArgumentException("report table headers must not be empty");
        }
        this.parent = parent;
        table.setHeaders(new ArrayList<>(Arrays.asList(headers)));
    }

    /**
     * 设置表格样式名称。
     *
     * @param styleName 样式名称
     * @return 当前构建器
     */
    public ReportTableBuilder style(String styleName) {
        table.setStyleName(styleName);
        return this;
    }

    /**
     * 设置列宽，单位为 point。
     *
     * @param widths 列宽数组
     * @return 当前构建器
     */
    public ReportTableBuilder widths(double... widths) {
        table.setColumnWidths(widths);
        return this;
    }

    /**
     * 设置题注。
     *
     * @param text 题注文本
     * @param autoNumbered 是否自动编号
     * @return 当前构建器
     */
    public ReportTableBuilder caption(String text, boolean autoNumbered) {
        ReportCaption caption = new ReportCaption(CaptionTargetType.TABLE, text);
        caption.setAutoNumbered(autoNumbered);
        table.setCaption(caption);
        return this;
    }

    /**
     * 追加文本行。
     *
     * @param values 单元格文本
     * @return 当前构建器
     */
    public ReportTableBuilder row(String... values) {
        if (values == null) {
            throw new IllegalArgumentException("report table row must not be null");
        }
        table.getRows().add(new ArrayList<>(Arrays.asList(values)));
        return this;
    }

    /**
     * 完成表格并返回所属章节。
     *
     * @return 所属章节构建器
     */
    public ReportSectionBuilder end() {
        parent.add(table);
        return parent;
    }

    /**
     * 获取正在构建的表格，主要供验证与高级扩展使用。
     *
     * @return 报告表格
     */
    public ReportTable getTable() {
        return table;
    }
}
