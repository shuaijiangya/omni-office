package cn.bugstack.export.document;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

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
     * 设置列宽比例权重，表格总宽度由当前页面正文宽度决定。
     *
     * @param widths 列宽比例权重，例如 {@code 1, 2, 1}
     * @return 当前构建器
     */
    public ReportTableBuilder widths(double... widths) {
        table.setColumnWidths(widths);
        return this;
    }

    /**
     * 设置表格水平对齐方式。
     *
     * @param alignment 左对齐、居中或右对齐
     * @return 当前构建器
     */
    public ReportTableBuilder alignment(ReportTableAlignment alignment) {
        table.setAlignment(alignment);
        return this;
    }

    /**
     * 设置表格内文本字体颜色。
     *
     * @param fontColor 字体颜色，格式为 {@code #RRGGBB}
     * @return 当前构建器
     */
    public ReportTableBuilder fontColor(String fontColor) {
        table.setFontColor(fontColor);
        return this;
    }

    /**
     * 动态设置当前表格的表头文本样式。
     *
     * @param customizer 表头样式配置回调
     * @return 当前构建器
     */
    public ReportTableBuilder headerTextStyle(Consumer<ReportTextRangeStyle> customizer) {
        if (customizer == null) {
            throw new IllegalArgumentException("report table header text style customizer must not be null");
        }
        ReportTextRangeStyle style = new ReportTextRangeStyle();
        customizer.accept(style);
        table.setHeaderTextStyle(style);
        return this;
    }

    /**
     * 动态设置当前表格的表内容文本样式。
     *
     * @param customizer 表内容样式配置回调
     * @return 当前构建器
     */
    public ReportTableBuilder bodyTextStyle(Consumer<ReportTextRangeStyle> customizer) {
        if (customizer == null) {
            throw new IllegalArgumentException("report table body text style customizer must not be null");
        }
        ReportTextRangeStyle style = new ReportTextRangeStyle();
        customizer.accept(style);
        table.setBodyTextStyle(style);
        return this;
    }

    /**
     * 添加矩形合并区域。行坐标包含表头，表头为第 {@code 0} 行。被覆盖单元格可以为空或重复
     * 左上角内容；若包含不同内容，文档校验会失败。
     *
     * @param startRow 起始行
     * @param startColumn 起始列
     * @param rowSpan 跨行数
     * @param columnSpan 跨列数
     * @return 当前构建器
     */
    public ReportTableBuilder merge(int startRow, int startColumn, int rowSpan, int columnSpan) {
        table.getMerges().add(new ReportTableMerge(startRow, startColumn, rowSpan, columnSpan));
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
        return caption(text, autoNumbered, CaptionPosition.BELOW);
    }

    /**
     * 设置题注文本、编号方式和上下位置。
     *
     * @param text 题注文本
     * @param autoNumbered 是否自动编号
     * @param position 题注位于表格上方或下方
     * @return 当前构建器
     */
    public ReportTableBuilder caption(String text, boolean autoNumbered, CaptionPosition position) {
        ReportCaption caption = new ReportCaption(CaptionTargetType.TABLE, text);
        caption.setAutoNumbered(autoNumbered);
        caption.setPosition(position);
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
