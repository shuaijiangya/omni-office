package cn.bugstack.export.example.composable.model;

import cn.bugstack.export.example.composable.ComposableReportModule;

/** 作战流程分析模块的数据对象。 */
public final class CombatProcessAnalysisModuleData implements ComposableModuleData {

    /** 当前示例写入 Word 的作战流程分析文本。 */
    private final String combatProcessAnalysis;

    public CombatProcessAnalysisModuleData(String combatProcessAnalysis) {
        this.combatProcessAnalysis = ModuleDataSupport.requiredText(
                combatProcessAnalysis, "combat process analysis");
    }

    public String getCombatProcessAnalysis() {
        return combatProcessAnalysis;
    }

    @Override
    public ComposableReportModule getModule() {
        return ComposableReportModule.COMBAT_PROCESS_ANALYSIS;
    }
}
