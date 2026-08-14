package cn.bugstack.export.example.composable.model;

import cn.bugstack.export.example.composable.ComposableReportModule;

/** 功能优化分析模块的数据对象。 */
public final class FunctionalOptimizationAnalysisModuleData implements ComposableModuleData {

    /** 当前示例写入 Word 的功能优化分析文本。 */
    private final String functionalOptimizationAnalysis;

    public FunctionalOptimizationAnalysisModuleData(String functionalOptimizationAnalysis) {
        this.functionalOptimizationAnalysis = ModuleDataSupport.requiredText(
                functionalOptimizationAnalysis, "functional optimization analysis");
    }

    public String getFunctionalOptimizationAnalysis() {
        return functionalOptimizationAnalysis;
    }

    @Override
    public ComposableReportModule getModule() {
        return ComposableReportModule.FUNCTIONAL_OPTIMIZATION_ANALYSIS;
    }
}
