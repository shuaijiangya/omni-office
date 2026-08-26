package cn.bugstack.protocol.document.block;

import java.util.ArrayList;
import java.util.List;

/**
 * 图表中的一个数据系列。
 */
public final class ChartSeriesSpec {

    private String name;
    private List<Double> values = new ArrayList<>();

    /** @return 系列名称 */
    public String getName() { return name; }

    /** @param name 系列名称 */
    public void setName(String name) { this.name = name; }

    /** @return 与分类一一对应的数据值 */
    public List<Double> getValues() { return values; }

    /** @param values 与分类一一对应的数据值 */
    public void setValues(List<Double> values) {
        this.values = values == null ? new ArrayList<>() : values;
    }
}
