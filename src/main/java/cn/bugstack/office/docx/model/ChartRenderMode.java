package cn.bugstack.office.docx.model;

/** DOCX 中图表的输出方式。 */
public enum ChartRenderMode {
    /** 可在 Word 中编辑数据和系列的原生图表。 */
    NATIVE_WORD,
    /** ECharts 渲染的静态 SVG，不能在 Word 中编辑图表数据。 */
    ECHARTS_IMAGE
}
