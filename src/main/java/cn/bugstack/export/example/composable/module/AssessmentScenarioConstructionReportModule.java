package cn.bugstack.export.example.composable.module;

import cn.bugstack.export.document.ReportSectionBuilder;
import cn.bugstack.export.example.composable.ComposableReportModule;
import cn.bugstack.export.example.composable.model.AssessmentScenarioConstructionModuleData;
import cn.bugstack.export.module.AbstractReportModule;
import cn.bugstack.export.module.ModuleDescriptor;
import cn.bugstack.export.module.ReportDataKey;
import cn.bugstack.export.module.ReportModuleContext;

/** 评估场景构设模块的独立实现。 */
public final class AssessmentScenarioConstructionReportModule extends AbstractReportModule<AssessmentScenarioConstructionModuleData> {

    public static final String CODE = ComposableReportModule.ASSESSMENT_SCENARIO_CONSTRUCTION.getCode();
    public static final ReportDataKey<AssessmentScenarioConstructionModuleData> DATA_KEY =
            ReportDataKey.of("composable-report.assessment-scenario-construction",
                    AssessmentScenarioConstructionModuleData.class);
    public static final ModuleDescriptor<AssessmentScenarioConstructionModuleData> DESCRIPTOR =
            ModuleDescriptor.of(CODE, ComposableReportModule.ASSESSMENT_SCENARIO_CONSTRUCTION.getTitle(), DATA_KEY);

    @Override
    public ModuleDescriptor<AssessmentScenarioConstructionModuleData> descriptor() {
        return DESCRIPTOR;
    }

    @Override
    protected void composeContent(ReportSectionBuilder section, AssessmentScenarioConstructionModuleData data,
                                  ReportModuleContext context) {
        section.paragraph(data.getScenarioConstruction());
    }
}
