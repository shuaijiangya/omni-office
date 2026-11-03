package cn.bugstack.application.completion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 在源 DOCX 的指定段落后插入一张可编辑 Word 原生图表的计划。 */
public final class ChartPatchPlan {

    private final String anchorParagraphId;
    private final String chartType;
    private final String title;
    private final List<String> categories;
    private final String seriesName;
    private final List<Double> values;
    private final List<String> valueEvidencePaths;

    /** 创建仅包含一个数据系列的图表补全计划。 */
    public ChartPatchPlan(String anchorParagraphId, String chartType, String title,
                          List<String> categories, String seriesName, List<Double> values) {
        this(anchorParagraphId, chartType, title, categories, seriesName, values,
                Collections.emptyList());
    }

    /** 创建包含每个数值在业务事实中 JSON Pointer 的可追溯图表计划。 */
    public ChartPatchPlan(String anchorParagraphId, String chartType, String title,
                          List<String> categories, String seriesName, List<Double> values,
                          List<String> valueEvidencePaths) {
        this.anchorParagraphId = anchorParagraphId;
        this.chartType = chartType;
        this.title = title;
        this.categories = Collections.unmodifiableList(new ArrayList<>(categories));
        this.seriesName = seriesName;
        this.values = Collections.unmodifiableList(new ArrayList<>(values));
        this.valueEvidencePaths = Collections.unmodifiableList(new ArrayList<>(valueEvidencePaths));
    }

    /** @return 源文档中图表应跟随的段落 ID */
    public String getAnchorParagraphId() { return anchorParagraphId; }

    /** @return COLUMN、BAR、PIE、LINE 或 RADAR */
    public String getChartType() { return chartType; }

    /** @return 图表标题 */
    public String getTitle() { return title; }

    /** @return 类别名称 */
    public List<String> getCategories() { return categories; }

    /** @return 数据系列名称 */
    public String getSeriesName() { return seriesName; }

    /** @return 与类别逐项对应的数值 */
    public List<Double> getValues() { return values; }

    /** @return 与数值一一对应的业务事实 JSON Pointer，可由审核运行记录追溯 */
    public List<String> getValueEvidencePaths() { return valueEvidencePaths; }
}
