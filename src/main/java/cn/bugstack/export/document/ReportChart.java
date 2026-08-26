package cn.bugstack.export.document;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 报告语义层的 Word 原生图表。 */
public final class ReportChart implements ReportElement {

    private ReportChartType chartType = ReportChartType.COLUMN;
    private String title;
    private final List<String> categories = new ArrayList<>();
    private final List<ReportChartSeries> series = new ArrayList<>();
    private double widthPoints = 460D;
    private double heightPoints = 280D;
    private boolean legendVisible = true;
    private ReportChartLegendPosition legendPosition = ReportChartLegendPosition.BOTTOM;
    private boolean showValues;
    private boolean showPercentages;
    private String categoryAxisTitle;
    private String valueAxisTitle;
    private ReportCaption caption;

    /** @return 图表元素类型 */
    @Override public ReportElementType getElementType() { return ReportElementType.CHART; }

    /** @return 图表类型 */ public ReportChartType getChartType() { return chartType; }
    /** @param chartType 图表类型 */ public void setChartType(ReportChartType chartType) { this.chartType = chartType; }
    /** @return 图表标题 */ public String getTitle() { return title; }
    /** @param title 图表标题 */ public void setTitle(String title) { this.title = title; }
    /** @return 分类标签 */ public List<String> getCategories() { return Collections.unmodifiableList(categories); }
    /** @param values 分类标签 */ public void setCategories(List<String> values) { categories.clear(); if (values != null) categories.addAll(values); }
    /** @return 数据系列 */ public List<ReportChartSeries> getSeries() { return Collections.unmodifiableList(series); }
    /** @param values 数据系列 */ public void setSeries(List<ReportChartSeries> values) { series.clear(); if (values != null) series.addAll(values); }
    /** @return 宽度 */ public double getWidthPoints() { return widthPoints; }
    /** @param widthPoints 宽度 */ public void setWidthPoints(double widthPoints) { this.widthPoints = widthPoints; }
    /** @return 高度 */ public double getHeightPoints() { return heightPoints; }
    /** @param heightPoints 高度 */ public void setHeightPoints(double heightPoints) { this.heightPoints = heightPoints; }
    /** @return 是否显示图例 */ public boolean isLegendVisible() { return legendVisible; }
    /** @param legendVisible 是否显示图例 */ public void setLegendVisible(boolean legendVisible) { this.legendVisible = legendVisible; }
    /** @return 图例位置 */ public ReportChartLegendPosition getLegendPosition() { return legendPosition; }
    /** @param legendPosition 图例位置 */ public void setLegendPosition(ReportChartLegendPosition legendPosition) { this.legendPosition = legendPosition; }
    /** @return 是否显示数值 */ public boolean isShowValues() { return showValues; }
    /** @param showValues 是否显示数值 */ public void setShowValues(boolean showValues) { this.showValues = showValues; }
    /** @return 是否显示百分比 */ public boolean isShowPercentages() { return showPercentages; }
    /** @param value 是否显示百分比 */ public void setShowPercentages(boolean value) { showPercentages = value; }
    /** @return 分类轴标题 */ public String getCategoryAxisTitle() { return categoryAxisTitle; }
    /** @param value 分类轴标题 */ public void setCategoryAxisTitle(String value) { categoryAxisTitle = value; }
    /** @return 数值轴标题 */ public String getValueAxisTitle() { return valueAxisTitle; }
    /** @param value 数值轴标题 */ public void setValueAxisTitle(String value) { valueAxisTitle = value; }
    /** @return 图题 */ public ReportCaption getCaption() { return caption; }
    /** @param caption 图题 */ public void setCaption(ReportCaption caption) { this.caption = caption; }
}
