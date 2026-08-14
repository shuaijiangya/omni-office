package cn.bugstack.export.example.composable.module;

import cn.bugstack.export.example.composable.ComposableReportModule;
import cn.bugstack.export.example.composable.model.AssessmentScenarioConstructionModuleData;
import cn.bugstack.export.module.AbstractTextReportModule;
import cn.bugstack.export.module.ModuleDescriptor;
import cn.bugstack.export.module.ReportDataKey;

/** 评估场景构设模块的独立实现。 */
public final class AssessmentScenarioConstructionReportModule extends AbstractTextReportModule<AssessmentScenarioConstructionModuleData> {

    public static final String CODE = ComposableReportModule.ASSESSMENT_SCENARIO_CONSTRUCTION.getCode();
    public static final ReportDataKey<AssessmentScenarioConstructionModuleData> DATA_KEY =
            ReportDataKey.of("composable-report.assessment-scenario-construction",
                    AssessmentScenarioConstructionModuleData.class);
    public static final ModuleDescriptor<AssessmentScenarioConstructionModuleData> DESCRIPTOR =
            ModuleDescriptor.of(CODE, ComposableReportModule.ASSESSMENT_SCENARIO_CONSTRUCTION.getTitle(), DATA_KEY);

    public AssessmentScenarioConstructionReportModule() {
        super(DESCRIPTOR, AssessmentScenarioConstructionModuleData::getScenarioConstruction);
    }
}
