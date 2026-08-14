package cn.bugstack.export.example.composable.model;

import cn.bugstack.export.example.composable.ComposableReportModule;

/** 贡献率分析模块的数据对象。 */
public final class ContributionRateAnalysisModuleData implements ComposableModuleData {

    /** 当前示例写入 Word 的贡献率分析文本。 */
    private final String contributionRateAnalysis;

    public ContributionRateAnalysisModuleData(String contributionRateAnalysis) {
        this.contributionRateAnalysis = ModuleDataSupport.requiredText(
                contributionRateAnalysis, "contribution rate analysis");
    }

    public String getContributionRateAnalysis() {
        return contributionRateAnalysis;
    }

    @Override
    public ComposableReportModule getModule() {
        return ComposableReportModule.CONTRIBUTION_RATE_ANALYSIS;
    }
}
