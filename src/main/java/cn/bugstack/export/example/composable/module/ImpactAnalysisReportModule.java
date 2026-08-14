package cn.bugstack.export.example.composable.module;

import cn.bugstack.export.example.composable.ComposableReportModule;
import cn.bugstack.export.example.composable.model.ImpactAnalysisModuleData;
import cn.bugstack.export.module.AbstractTextReportModule;
import cn.bugstack.export.module.ModuleDescriptor;
import cn.bugstack.export.module.ReportDataKey;

/** 影响分析模块的独立实现。 */
public final class ImpactAnalysisReportModule extends AbstractTextReportModule<ImpactAnalysisModuleData> {

    public static final String CODE = ComposableReportModule.IMPACT_ANALYSIS.getCode();
    public static final ReportDataKey<ImpactAnalysisModuleData> DATA_KEY =
            ReportDataKey.of("composable-report.impact-analysis", ImpactAnalysisModuleData.class);
    public static final ModuleDescriptor<ImpactAnalysisModuleData> DESCRIPTOR =
            ModuleDescriptor.of(CODE, ComposableReportModule.IMPACT_ANALYSIS.getTitle(), DATA_KEY);

    public ImpactAnalysisReportModule() {
        super(DESCRIPTOR, ImpactAnalysisModuleData::getImpactAnalysis);
    }
}
