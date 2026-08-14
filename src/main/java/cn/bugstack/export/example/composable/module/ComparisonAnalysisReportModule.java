package cn.bugstack.export.example.composable.module;

import cn.bugstack.export.example.composable.ComposableReportModule;
import cn.bugstack.export.example.composable.model.ComparisonAnalysisModuleData;
import cn.bugstack.export.module.AbstractTextReportModule;
import cn.bugstack.export.module.ModuleDescriptor;
import cn.bugstack.export.module.ReportDataKey;

/** 对比分析模块的独立实现。 */
public final class ComparisonAnalysisReportModule extends AbstractTextReportModule<ComparisonAnalysisModuleData> {

    public static final String CODE = ComposableReportModule.COMPARISON_ANALYSIS.getCode();
    public static final ReportDataKey<ComparisonAnalysisModuleData> DATA_KEY =
            ReportDataKey.of("composable-report.comparison-analysis", ComparisonAnalysisModuleData.class);
    public static final ModuleDescriptor<ComparisonAnalysisModuleData> DESCRIPTOR =
            ModuleDescriptor.of(CODE, ComposableReportModule.COMPARISON_ANALYSIS.getTitle(), DATA_KEY);

    public ComparisonAnalysisReportModule() {
        super(DESCRIPTOR, ComparisonAnalysisModuleData::getComparisonAnalysis);
    }
}
