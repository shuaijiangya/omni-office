package cn.bugstack.office.docx.model;

/**
 * 段落中的 Word 原生图表行内节点。
 *
 * <p>图表在 OOXML 中属于 drawing，既可以由 {@code SectionBuilder.chart(...)} 作为独立块构建，
 * 也可以由 {@code SectionBuilder.paragraph().chart(...)} 放入明确的段落中。</p>
 */
public final class ChartInline implements DocxInline {

    private final ChartNode chart;

    /**
     * 创建图表行内节点。
     *
     * @param chart 图表数据节点
     */
    public ChartInline(ChartNode chart) {
        if (chart == null) throw new IllegalArgumentException("inline chart must not be null");
        this.chart = chart;
    }

    /**
     * 获取图表数据节点。
     *
     * @return 图表数据节点
     */
    public ChartNode getChart() {
        return chart;
    }
}
