package cn.bugstack.export.document;

/** Word 原生图表类型。 */
public enum ReportChartType {
    /** 纵向柱状图，也用于多系列对比图。 */ COLUMN,
    /** 横向条形图，也用于单指标单样本对比图。 */ BAR,
    /** 饼图。 */ PIE,
    /** 折线图。 */ LINE,
    /** 雷达图。 */ RADAR
}
