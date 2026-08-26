package cn.bugstack.office.docx.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 可渲染为 Word 原生可编辑图表的块级节点。 */
public final class ChartNode implements DocxBlock {
    private ChartType chartType = ChartType.COLUMN;
    private String title;
    private final List<String> categories = new ArrayList<>();
    private final List<ChartSeriesNode> series = new ArrayList<>();
    private double widthPoints = 460D;
    private double heightPoints = 280D;
    private boolean legendVisible = true;
    private ChartLegendPosition legendPosition = ChartLegendPosition.BOTTOM;
    private boolean showValues;
    private boolean showPercentages;
    private String categoryAxisTitle;
    private String valueAxisTitle;

    /** @return 图表类型 */ public ChartType getChartType() { return chartType; }
    /** @param chartType 图表类型 */ public void setChartType(ChartType chartType) { this.chartType = chartType; }
    /** @return 标题 */ public String getTitle() { return title; }
    /** @param title 标题 */ public void setTitle(String title) { this.title = title; }
    /** @return 分类标签 */ public List<String> getCategories() { return Collections.unmodifiableList(categories); }
    /** @param values 分类标签 */ public void setCategories(List<String> values) { categories.clear(); if (values != null) categories.addAll(values); }
    /** @return 数据系列 */ public List<ChartSeriesNode> getSeries() { return Collections.unmodifiableList(series); }
    /** @param value 数据系列 */ public void addSeries(ChartSeriesNode value) { series.add(value); }
    /** @return 宽度 */ public double getWidthPoints() { return widthPoints; }
    /** @param value 宽度 */ public void setWidthPoints(double value) { widthPoints = value; }
    /** @return 高度 */ public double getHeightPoints() { return heightPoints; }
    /** @param value 高度 */ public void setHeightPoints(double value) { heightPoints = value; }
    /** @return 是否显示图例 */ public boolean isLegendVisible() { return legendVisible; }
    /** @param value 是否显示图例 */ public void setLegendVisible(boolean value) { legendVisible = value; }
    /** @return 图例位置 */ public ChartLegendPosition getLegendPosition() { return legendPosition; }
    /** @param value 图例位置 */ public void setLegendPosition(ChartLegendPosition value) { legendPosition = value; }
    /** @return 是否显示数值 */ public boolean isShowValues() { return showValues; }
    /** @param value 是否显示数值 */ public void setShowValues(boolean value) { showValues = value; }
    /** @return 是否显示百分比 */ public boolean isShowPercentages() { return showPercentages; }
    /** @param value 是否显示百分比 */ public void setShowPercentages(boolean value) { showPercentages = value; }
    /** @return 分类轴标题 */ public String getCategoryAxisTitle() { return categoryAxisTitle; }
    /** @param value 分类轴标题 */ public void setCategoryAxisTitle(String value) { categoryAxisTitle = value; }
    /** @return 数值轴标题 */ public String getValueAxisTitle() { return valueAxisTitle; }
    /** @param value 数值轴标题 */ public void setValueAxisTitle(String value) { valueAxisTitle = value; }
}
