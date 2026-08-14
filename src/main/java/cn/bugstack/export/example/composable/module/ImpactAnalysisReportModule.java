package cn.bugstack.export.example.composable.module;

import cn.bugstack.export.document.ReportSectionBuilder;
import cn.bugstack.export.example.composable.ComposableReportModule;
import cn.bugstack.export.example.composable.model.ImpactAnalysisModuleData;
import cn.bugstack.export.module.AbstractReportModule;
import cn.bugstack.export.module.ModuleDescriptor;
import cn.bugstack.export.module.ReportDataKey;
import cn.bugstack.export.module.ReportModuleContext;

/** 影响分析模块的独立实现。 */
public final class ImpactAnalysisReportModule extends AbstractReportModule<ImpactAnalysisModuleData> {

    public static final String CODE = ComposableReportModule.IMPACT_ANALYSIS.getCode();
    public static final ReportDataKey<ImpactAnalysisModuleData> DATA_KEY =
            ReportDataKey.of("composable-report.impact-analysis", ImpactAnalysisModuleData.class);
    public static final ModuleDescriptor<ImpactAnalysisModuleData> DESCRIPTOR =
            ModuleDescriptor.of(CODE, ComposableReportModule.IMPACT_ANALYSIS.getTitle(), DATA_KEY);

    @Override
    public ModuleDescriptor<ImpactAnalysisModuleData> descriptor() {
        return DESCRIPTOR;
    }

    @Override
    protected void composeContent(ReportSectionBuilder section, ImpactAnalysisModuleData data,
                                  ReportModuleContext context) {
        section.paragraph(data.getImpactAnalysis());
    }
}
