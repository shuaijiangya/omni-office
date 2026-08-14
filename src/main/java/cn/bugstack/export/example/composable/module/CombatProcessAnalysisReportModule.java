package cn.bugstack.export.example.composable.module;

import cn.bugstack.export.example.composable.ComposableReportModule;
import cn.bugstack.export.example.composable.model.CombatProcessAnalysisModuleData;
import cn.bugstack.export.module.AbstractTextReportModule;
import cn.bugstack.export.module.ModuleDescriptor;
import cn.bugstack.export.module.ReportDataKey;

/** 作战流程分析模块的独立实现。 */
public final class CombatProcessAnalysisReportModule extends AbstractTextReportModule<CombatProcessAnalysisModuleData> {

    public static final String CODE = ComposableReportModule.COMBAT_PROCESS_ANALYSIS.getCode();
    public static final ReportDataKey<CombatProcessAnalysisModuleData> DATA_KEY =
            ReportDataKey.of("composable-report.combat-process-analysis", CombatProcessAnalysisModuleData.class);
    public static final ModuleDescriptor<CombatProcessAnalysisModuleData> DESCRIPTOR =
            ModuleDescriptor.of(CODE, ComposableReportModule.COMBAT_PROCESS_ANALYSIS.getTitle(), DATA_KEY);

    public CombatProcessAnalysisReportModule() {
        super(DESCRIPTOR, CombatProcessAnalysisModuleData::getCombatProcessAnalysis);
    }
}
