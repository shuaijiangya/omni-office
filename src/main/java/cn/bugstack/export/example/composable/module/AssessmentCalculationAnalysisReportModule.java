package cn.bugstack.export.example.composable.module;

import cn.bugstack.export.document.ReportSectionBuilder;
import cn.bugstack.export.example.composable.ComposableReportModule;
import cn.bugstack.export.example.composable.model.AssessmentCalculationAnalysisModuleData;
import cn.bugstack.export.module.AbstractReportModule;
import cn.bugstack.export.module.ModuleDescriptor;
import cn.bugstack.export.module.ReportDataKey;
import cn.bugstack.export.module.ReportModuleContext;

/** 评估计算分析模块的独立实现。 */
public final class AssessmentCalculationAnalysisReportModule extends AbstractReportModule<AssessmentCalculationAnalysisModuleData> {

    public static final String CODE = ComposableReportModule.ASSESSMENT_CALCULATION_ANALYSIS.getCode();
    public static final ReportDataKey<AssessmentCalculationAnalysisModuleData> DATA_KEY =
            ReportDataKey.of("composable-report.assessment-calculation-analysis",
                    AssessmentCalculationAnalysisModuleData.class);
    public static final ModuleDescriptor<AssessmentCalculationAnalysisModuleData> DESCRIPTOR =
            ModuleDescriptor.of(CODE, ComposableReportModule.ASSESSMENT_CALCULATION_ANALYSIS.getTitle(), DATA_KEY);

    @Override
    public ModuleDescriptor<AssessmentCalculationAnalysisModuleData> descriptor() {
        return DESCRIPTOR;
    }

    @Override
    protected void composeContent(ReportSectionBuilder section, AssessmentCalculationAnalysisModuleData data,
                                  ReportModuleContext context) {
        section.paragraph(data.getCalculationAnalysis());
    }
}
