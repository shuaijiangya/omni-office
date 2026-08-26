package cn.bugstack.export.document;

import java.util.Arrays;

/** 面向业务模块的图表构建器。 */
public final class ReportChartBuilder {

    private final ReportSectionBuilder parent;
    private final ReportChart chart = new ReportChart();

    ReportChartBuilder(ReportSectionBuilder parent, ReportChartType type) {
        this.parent = parent;
        chart.setChartType(type);
    }

    /** @param title 图表标题 @return 当前构建器 */
    public ReportChartBuilder title(String title) { chart.setTitle(title); return this; }

    /** @param categories 分类标签 @return 当前构建器 */
    public ReportChartBuilder categories(String... categories) {
        chart.setCategories(Arrays.asList(categories)); return this;
    }

    /** @param name 系列名称 @param values 系列数值 @return 当前构建器 */
    public ReportChartBuilder series(String name, Double... values) {
        java.util.List<ReportChartSeries> all = new java.util.ArrayList<>(chart.getSeries());
        all.add(new ReportChartSeries(name, Arrays.asList(values)));
        chart.setSeries(all);
        return this;
    }

    /** @param widthPoints 宽度 @param heightPoints 高度 @return 当前构建器 */
    public ReportChartBuilder size(double widthPoints, double heightPoints) {
        chart.setWidthPoints(widthPoints); chart.setHeightPoints(heightPoints); return this;
    }

    /** @param visible 是否显示 @param position 图例位置 @return 当前构建器 */
    public ReportChartBuilder legend(boolean visible, ReportChartLegendPosition position) {
        chart.setLegendVisible(visible); chart.setLegendPosition(position); return this;
    }

    /** @param show 是否显示数据值 @return 当前构建器 */
    public ReportChartBuilder showValues(boolean show) { chart.setShowValues(show); return this; }

    /** @param show 是否显示百分比 @return 当前构建器 */
    public ReportChartBuilder showPercentages(boolean show) { chart.setShowPercentages(show); return this; }

    /** @param categoryTitle 分类轴标题 @param valueTitle 数值轴标题 @return 当前构建器 */
    public ReportChartBuilder axisTitles(String categoryTitle, String valueTitle) {
        chart.setCategoryAxisTitle(categoryTitle); chart.setValueAxisTitle(valueTitle); return this;
    }

    /** @param text 图题 @param position 图题位置 @return 当前构建器 */
    public ReportChartBuilder caption(String text, CaptionPosition position) {
        ReportCaption caption = new ReportCaption(CaptionTargetType.IMAGE, text);
        caption.setPosition(position);
        chart.setCaption(caption);
        return this;
    }

    /** @return 父章节构建器 */
    public ReportSectionBuilder end() { return parent.add(chart); }
}
