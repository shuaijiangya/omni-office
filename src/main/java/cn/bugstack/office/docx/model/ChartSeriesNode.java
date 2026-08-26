package cn.bugstack.office.docx.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** DOCX 图表数据系列节点。 */
public final class ChartSeriesNode {
    private final String name;
    private final List<Double> values;

    /** @param name 系列名称 @param values 系列数值 */
    public ChartSeriesNode(String name, List<Double> values) {
        this.name = name;
        this.values = values == null ? new ArrayList<>() : new ArrayList<>(values);
    }

    /** @return 系列名称 */ public String getName() { return name; }
    /** @return 不可修改的系列数值 */ public List<Double> getValues() { return Collections.unmodifiableList(values); }
}
