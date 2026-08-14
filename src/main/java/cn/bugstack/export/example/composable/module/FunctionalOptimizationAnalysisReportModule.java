package cn.bugstack.export.example.composable.module;

import cn.bugstack.export.document.ReportSectionBuilder;
import cn.bugstack.export.example.composable.ComposableReportModule;
import cn.bugstack.export.example.composable.model.FunctionalOptimizationAnalysisModuleData;
import cn.bugstack.export.module.AbstractReportModule;
import cn.bugstack.export.module.ModuleDescriptor;
import cn.bugstack.export.module.ReportDataKey;
import cn.bugstack.export.module.ReportModuleContext;

/** 功能优化分析模块的独立实现。 */
public final class FunctionalOptimizationAnalysisReportModule extends AbstractReportModule<FunctionalOptimizationAnalysisModuleData> {

    public static final String CODE = ComposableReportModule.FUNCTIONAL_OPTIMIZATION_ANALYSIS.getCode();
    public static final ReportDataKey<FunctionalOptimizationAnalysisModuleData> DATA_KEY =
            ReportDataKey.of("composable-report.functional-optimization-analysis",
                    FunctionalOptimizationAnalysisModuleData.class);
    public static final ModuleDescriptor<FunctionalOptimizationAnalysisModuleData> DESCRIPTOR =
            ModuleDescriptor.of(CODE, ComposableReportModule.FUNCTIONAL_OPTIMIZATION_ANALYSIS.getTitle(), DATA_KEY);

    @Override
    public ModuleDescriptor<FunctionalOptimizationAnalysisModuleData> descriptor() {
        return DESCRIPTOR;
    }

    @Override
    protected void composeContent(ReportSectionBuilder section, FunctionalOptimizationAnalysisModuleData data,
                                  ReportModuleContext context) {
        section.paragraph(data.getFunctionalOptimizationAnalysis());
    }
}
