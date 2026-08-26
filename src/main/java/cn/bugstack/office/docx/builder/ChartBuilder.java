package cn.bugstack.office.docx.builder;

import cn.bugstack.office.docx.model.ChartLegendPosition;
import cn.bugstack.office.docx.model.ChartNode;
import cn.bugstack.office.docx.model.ChartSeriesNode;

import java.util.Arrays;

/**
 * DOCX 原生图表构建器。
 *
 * @param <P> 父级 Builder 类型
 */
public final class ChartBuilder<P> {
    private final P parent;
    private final ChartNode chart;

    /** @param parent 父构建器 @param chart 图表节点 */
    public ChartBuilder(P parent, ChartNode chart) { this.parent = parent; this.chart = chart; }

    /** @param value 标题 @return 当前构建器 */
    public ChartBuilder<P> title(String value) { chart.setTitle(value); return this; }
    /** @param values 分类标签 @return 当前构建器 */
    public ChartBuilder<P> categories(String... values) { chart.setCategories(Arrays.asList(values)); return this; }
    /** @param name 系列名称 @param values 数值 @return 当前构建器 */
    public ChartBuilder<P> series(String name, Double... values) {
        chart.addSeries(new ChartSeriesNode(name, Arrays.asList(values))); return this;
    }
    /** @param widthPoints 宽度 @param heightPoints 高度 @return 当前构建器 */
    public ChartBuilder<P> size(double widthPoints, double heightPoints) {
        chart.setWidthPoints(widthPoints); chart.setHeightPoints(heightPoints); return this;
    }
    /** @param visible 是否显示 @param position 位置 @return 当前构建器 */
    public ChartBuilder<P> legend(boolean visible, ChartLegendPosition position) {
        chart.setLegendVisible(visible); chart.setLegendPosition(position); return this;
    }
    /** @param value 是否显示数据值 @return 当前构建器 */
    public ChartBuilder<P> showValues(boolean value) { chart.setShowValues(value); return this; }
    /** @param value 是否显示百分比 @return 当前构建器 */
    public ChartBuilder<P> showPercentages(boolean value) { chart.setShowPercentages(value); return this; }
    /** @param categoryTitle 分类轴标题 @param valueTitle 数值轴标题 @return 当前构建器 */
    public ChartBuilder<P> axisTitles(String categoryTitle, String valueTitle) {
        chart.setCategoryAxisTitle(categoryTitle); chart.setValueAxisTitle(valueTitle); return this;
    }
    /** @return 父构建器 */ public P end() { return parent; }
}
