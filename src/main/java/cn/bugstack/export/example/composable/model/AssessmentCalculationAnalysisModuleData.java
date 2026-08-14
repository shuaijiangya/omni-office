package cn.bugstack.export.example.composable.model;

import cn.bugstack.export.example.composable.ComposableReportModule;

/** 评估计算分析模块的数据对象。 */
public final class AssessmentCalculationAnalysisModuleData implements ComposableModuleData {

    /** 当前示例写入 Word 的评估计算分析文本。 */
    private final String calculationAnalysis;

    public AssessmentCalculationAnalysisModuleData(String calculationAnalysis) {
        this.calculationAnalysis = ModuleDataSupport.requiredText(
                calculationAnalysis, "assessment calculation analysis");
    }

    public String getCalculationAnalysis() {
        return calculationAnalysis;
    }

    @Override
    public ComposableReportModule getModule() {
        return ComposableReportModule.ASSESSMENT_CALCULATION_ANALYSIS;
    }
}
