package cn.bugstack.protocol.document.block;

import java.util.ArrayList;
import java.util.List;

/**
 * Word 原生图表块。
 *
 * <p>支持柱状图、条形图、饼图、折线图和雷达图。对比图可使用多系列
 * {@code COLUMN}/{@code BAR}，也可使用单分类、单系列的 {@code BAR}
 * 表达横向单指标单样本结果。</p>
 */
public final class ChartBlockSpec extends BlockSpec {

    private String chartType = "COLUMN";
    private String title;
    private List<String> categories = new ArrayList<>();
    private List<ChartSeriesSpec> series = new ArrayList<>();
    private double widthPoints = 460D;
    private double heightPoints = 280D;
    private boolean legendVisible = true;
    private String legendPosition = "BOTTOM";
    private boolean showValues;
    private boolean showPercentages;
    private String categoryAxisTitle;
    private String valueAxisTitle;
    private String caption;
    private boolean captionAutoNumbered = true;
    private String captionPosition = "BELOW";

    /** @return 图表类型 */
    public String getChartType() { return chartType; }

    /** @param chartType COLUMN、BAR、PIE、LINE 或 RADAR */
    public void setChartType(String chartType) { this.chartType = chartType; }

    /** @return 图表标题 */
    public String getTitle() { return title; }

    /** @param title 图表标题 */
    public void setTitle(String title) { this.title = title; }

    /** @return 分类轴标签 */
    public List<String> getCategories() { return categories; }

    /** @param categories 分类轴标签 */
    public void setCategories(List<String> categories) {
        this.categories = categories == null ? new ArrayList<>() : categories;
    }

    /** @return 数据系列 */
    public List<ChartSeriesSpec> getSeries() { return series; }

    /** @param series 数据系列 */
    public void setSeries(List<ChartSeriesSpec> series) {
        this.series = series == null ? new ArrayList<>() : series;
    }

    /** @return 图表宽度，单位为 point */
    public double getWidthPoints() { return widthPoints; }

    /** @param widthPoints 图表宽度，单位为 point */
    public void setWidthPoints(double widthPoints) { this.widthPoints = widthPoints; }

    /** @return 图表高度，单位为 point */
    public double getHeightPoints() { return heightPoints; }

    /** @param heightPoints 图表高度，单位为 point */
    public void setHeightPoints(double heightPoints) { this.heightPoints = heightPoints; }

    /** @return 是否显示图例 */
    public boolean isLegendVisible() { return legendVisible; }

    /** @param legendVisible 是否显示图例 */
    public void setLegendVisible(boolean legendVisible) { this.legendVisible = legendVisible; }

    /** @return 图例位置 */
    public String getLegendPosition() { return legendPosition; }

    /** @param legendPosition BOTTOM、TOP、LEFT 或 RIGHT */
    public void setLegendPosition(String legendPosition) { this.legendPosition = legendPosition; }

    /** @return 是否显示数据值 */
    public boolean isShowValues() { return showValues; }

    /** @param showValues 是否显示数据值 */
    public void setShowValues(boolean showValues) { this.showValues = showValues; }

    /** @return 是否显示百分比，仅饼图有效 */
    public boolean isShowPercentages() { return showPercentages; }

    /** @param showPercentages 是否显示百分比，仅饼图有效 */
    public void setShowPercentages(boolean showPercentages) { this.showPercentages = showPercentages; }

    /** @return 分类轴标题 */
    public String getCategoryAxisTitle() { return categoryAxisTitle; }

    /** @param categoryAxisTitle 分类轴标题 */
    public void setCategoryAxisTitle(String categoryAxisTitle) { this.categoryAxisTitle = categoryAxisTitle; }

    /** @return 数值轴标题 */
    public String getValueAxisTitle() { return valueAxisTitle; }

    /** @param valueAxisTitle 数值轴标题 */
    public void setValueAxisTitle(String valueAxisTitle) { this.valueAxisTitle = valueAxisTitle; }

    /** @return 图题文本 */
    public String getCaption() { return caption; }

    /** @param caption 图题文本 */
    public void setCaption(String caption) { this.caption = caption; }

    /** @return 图题是否自动编号 */
    public boolean isCaptionAutoNumbered() { return captionAutoNumbered; }

    /** @param captionAutoNumbered 图题是否自动编号 */
    public void setCaptionAutoNumbered(boolean captionAutoNumbered) { this.captionAutoNumbered = captionAutoNumbered; }

    /** @return 图题位置 */
    public String getCaptionPosition() { return captionPosition; }

    /** @param captionPosition ABOVE 或 BELOW */
    public void setCaptionPosition(String captionPosition) { this.captionPosition = captionPosition; }
}
