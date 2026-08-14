package cn.bugstack.export.example.composable.model;

import cn.bugstack.export.example.composable.ComposableReportModule;

/** 对比分析模块的数据对象。 */
public final class ComparisonAnalysisModuleData implements ComposableModuleData {

    /** 当前示例写入 Word 的对比分析文本。 */
    private final String comparisonAnalysis;

    public ComparisonAnalysisModuleData(String comparisonAnalysis) {
        this.comparisonAnalysis = ModuleDataSupport.requiredText(comparisonAnalysis, "comparison analysis");
    }

    public String getComparisonAnalysis() {
        return comparisonAnalysis;
    }

    @Override
    public ComposableReportModule getModule() {
        return ComposableReportModule.COMPARISON_ANALYSIS;
    }
}
