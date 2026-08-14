package cn.bugstack.export.example.composable.module;

import cn.bugstack.export.example.composable.ComposableReportModule;
import cn.bugstack.export.example.composable.model.ContributionRateAnalysisModuleData;
import cn.bugstack.export.module.AbstractTextReportModule;
import cn.bugstack.export.module.ModuleDescriptor;
import cn.bugstack.export.module.ReportDataKey;

/** 贡献率分析模块的独立实现。 */
public final class ContributionRateAnalysisReportModule extends AbstractTextReportModule<ContributionRateAnalysisModuleData> {

    public static final String CODE = ComposableReportModule.CONTRIBUTION_RATE_ANALYSIS.getCode();
    public static final ReportDataKey<ContributionRateAnalysisModuleData> DATA_KEY =
            ReportDataKey.of("composable-report.contribution-rate-analysis",
                    ContributionRateAnalysisModuleData.class);
    public static final ModuleDescriptor<ContributionRateAnalysisModuleData> DESCRIPTOR =
            ModuleDescriptor.of(CODE, ComposableReportModule.CONTRIBUTION_RATE_ANALYSIS.getTitle(), DATA_KEY);

    public ContributionRateAnalysisReportModule() {
        super(DESCRIPTOR, ContributionRateAnalysisModuleData::getContributionRateAnalysis);
    }
}
