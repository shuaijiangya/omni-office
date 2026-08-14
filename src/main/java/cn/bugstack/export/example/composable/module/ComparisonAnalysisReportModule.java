package cn.bugstack.export.example.composable.module;

import cn.bugstack.export.document.ReportSectionBuilder;
import cn.bugstack.export.example.composable.ComposableReportModule;
import cn.bugstack.export.example.composable.model.ComparisonAnalysisModuleData;
import cn.bugstack.export.module.AbstractReportModule;
import cn.bugstack.export.module.ModuleDescriptor;
import cn.bugstack.export.module.ReportDataKey;
import cn.bugstack.export.module.ReportModuleContext;

/** 对比分析模块的独立实现。 */
public final class ComparisonAnalysisReportModule extends AbstractReportModule<ComparisonAnalysisModuleData> {

    public static final String CODE = ComposableReportModule.COMPARISON_ANALYSIS.getCode();
    public static final ReportDataKey<ComparisonAnalysisModuleData> DATA_KEY =
            ReportDataKey.of("composable-report.comparison-analysis", ComparisonAnalysisModuleData.class);
    public static final ModuleDescriptor<ComparisonAnalysisModuleData> DESCRIPTOR =
            ModuleDescriptor.of(CODE, ComposableReportModule.COMPARISON_ANALYSIS.getTitle(), DATA_KEY);

    @Override
    public ModuleDescriptor<ComparisonAnalysisModuleData> descriptor() {
        return DESCRIPTOR;
    }

    @Override
    protected void composeContent(ReportSectionBuilder section, ComparisonAnalysisModuleData data,
                                  ReportModuleContext context) {
        section.paragraph(data.getComparisonAnalysis());
    }
}
