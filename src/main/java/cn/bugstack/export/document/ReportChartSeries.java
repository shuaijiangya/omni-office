package cn.bugstack.export.document;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 报告图表中的一个数据系列。 */
public final class ReportChartSeries {

    private String name;
    private final List<Double> values = new ArrayList<>();

    /** 创建空数据系列。 */
    public ReportChartSeries() { }

    /** @param name 系列名称 @param values 系列数值 */
    public ReportChartSeries(String name, List<Double> values) {
        this.name = name;
        if (values != null) this.values.addAll(values);
    }

    /** @return 系列名称 */
    public String getName() { return name; }

    /** @param name 系列名称 */
    public void setName(String name) { this.name = name; }

    /** @return 不可修改的系列数值 */
    public List<Double> getValues() { return Collections.unmodifiableList(values); }

    /** @param values 系列数值 */
    public void setValues(List<Double> values) {
        this.values.clear();
        if (values != null) this.values.addAll(values);
    }
}
