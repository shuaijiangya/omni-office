package cn.bugstack.export.example.composable.model;

import cn.bugstack.export.example.composable.ComposableReportModule;

/** 影响分析模块的数据对象。 */
public final class ImpactAnalysisModuleData implements ComposableModuleData {

    /** 当前示例写入 Word 的影响分析文本。 */
    private final String impactAnalysis;

    public ImpactAnalysisModuleData(String impactAnalysis) {
        this.impactAnalysis = ModuleDataSupport.requiredText(impactAnalysis, "impact analysis");
    }

    public String getImpactAnalysis() {
        return impactAnalysis;
    }

    @Override
    public ComposableReportModule getModule() {
        return ComposableReportModule.IMPACT_ANALYSIS;
    }
}
