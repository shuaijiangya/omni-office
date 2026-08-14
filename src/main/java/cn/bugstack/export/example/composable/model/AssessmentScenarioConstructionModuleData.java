package cn.bugstack.export.example.composable.model;

import cn.bugstack.export.example.composable.ComposableReportModule;

/** 评估场景构设模块的数据对象。 */
public final class AssessmentScenarioConstructionModuleData implements ComposableModuleData {

    /** 当前示例写入 Word 的评估场景构设文本。 */
    private final String scenarioConstruction;

    public AssessmentScenarioConstructionModuleData(String scenarioConstruction) {
        this.scenarioConstruction = ModuleDataSupport.requiredText(
                scenarioConstruction, "assessment scenario construction");
    }

    public String getScenarioConstruction() {
        return scenarioConstruction;
    }

    @Override
    public ComposableReportModule getModule() {
        return ComposableReportModule.ASSESSMENT_SCENARIO_CONSTRUCTION;
    }
}
